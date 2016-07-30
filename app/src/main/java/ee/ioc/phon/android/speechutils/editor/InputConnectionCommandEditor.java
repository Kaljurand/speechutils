package ee.ioc.phon.android.speechutils.editor;

import android.text.TextUtils;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ee.ioc.phon.android.speechutils.Log;

/**
 * TODO: keep track of added spaces
 * TODO: the returned Op should never be null, however, run can return a null Op
 */
public class InputConnectionCommandEditor implements CommandEditor {

    // Maximum number of previous utterances that a command can contain
    private static final int MAX_UTT_IN_COMMAND = 3;

    // Maximum number of characters that left-swipe is willing to delete
    private static final int MAX_DELETABLE_CONTEXT = 100;
    // Token optionally preceded by whitespace
    private static final Pattern WHITESPACE_AND_TOKEN = Pattern.compile("\\s*\\w+");
    private static final Pattern SELREF = Pattern.compile("\\{\\}");
    private static final Pattern ALL = Pattern.compile("^(.*)$");

    private String mPrevText = "";
    private int mAddedLength = 0;

    // TODO: Restrict the size of these stacks

    // The command prefix is a list of consecutive final results whose concatenation can possibly
    // form a command. An item is added to the list for every final result that is not a command.
    // The list if cleared if a command is executed or if reset() is called.
    private List<String> mCommandPrefix = new ArrayList<>();
    private Deque<Op> mOpStack = new ArrayDeque<>();
    private Deque<Op> mUndoStack = new ArrayDeque<>();

    private InputConnection mInputConnection;

    private UtteranceRewriter mUtteranceRewriter;

    private CommandEditorManager mCommandEditorManager;

    public InputConnectionCommandEditor() {
        mCommandEditorManager = new CommandEditorManager(this);
    }

    public void setInputConnection(InputConnection inputConnection) {
        mInputConnection = inputConnection;
    }

    public InputConnection getInputConnection() {
        return mInputConnection;
    }

    @Override
    public void setUtteranceRewriter(UtteranceRewriter ur) {
        mUtteranceRewriter = ur;
    }

    @Override
    public void reset() {
        mCommandPrefix.clear();
        mPrevText = "";
        mAddedLength = 0;
    }

    /**
     * Writes the text into the text field or executes a command.
     */
    @Override
    public CommandEditorResult commitFinalResult(final String text) {
        CommandEditorResult result = null;
        if (mUtteranceRewriter == null) {
            // If rewrites/commands are not defined (default), then selection can be dictated over.
            commitWithOverwrite(text);
        } else {
            final ExtractedText et = getExtractedText();
            final String selectedText = getSelectedText();
            // Try to interpret the text as a command and if it is, then apply it.
            // Otherwise write out the text as usual.
            UtteranceRewriter.Rewrite rewrite = applyCommand(text);
            String textRewritten = rewrite.mStr;
            final int len = commitWithOverwrite(textRewritten);
            // TODO: add undo for setSelection even if len==0
            if (len > 0) {
                pushOpUndo(new Op("delete " + len) {
                    @Override
                    public Op run() {
                        mInputConnection.beginBatchEdit();
                        boolean success = mInputConnection.deleteSurroundingText(len, 0);
                        if (et != null && selectedText.length() > 0) {
                            success = mInputConnection.commitText(selectedText, 1) &&
                                    mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                        }
                        mInputConnection.endBatchEdit();
                        if (success) {
                            return NO_OP;
                        }
                        return null;
                    }
                });
            }
            boolean success = false;
            if (rewrite.isCommand()) {
                mCommandPrefix.clear();
                success = mCommandEditorManager.execute(rewrite.mId, rewrite.mArgs);
            } else {
                mCommandPrefix.add(textRewritten);
            }
            result = new CommandEditorResult(success, rewrite);
        }
        mPrevText = "";
        mAddedLength = 0;
        return result;
    }

    /**
     * Writes the text into the text field and stores it for future reference.
     * If there is a selection then partial results are not written out.
     */
    @Override
    public boolean commitPartialResult(String text) {
        CharSequence cs = mInputConnection.getSelectedText(0);
        if (cs != null && cs.length() > 0) {
            return false;
        }
        String textRewritten = rewrite(text);
        commitWithOverwrite(textRewritten);
        mPrevText = textRewritten;

        return true;
    }

    @Override
    public CharSequence getText() {
        ExtractedText et = getExtractedText();
        if (et == null) {
            return null;
        }
        return et.text;
    }

    @Override
    public Op goUp() {
        return new Op("goUp") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))) {
                    return goDown();
                }
                return null;
            }
        };
    }

    @Override
    public Op goDown() {
        return new Op("goDown") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))) {
                    return goUp();
                }
                return null;
            }
        };
    }

    @Override
    public Op goLeft() {
        return new Op("goLeft") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))) {
                    return goRight();
                }
                return null;
            }
        };
    }

    @Override
    public Op goRight() {
        return new Op("goRight") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))) {
                    return goLeft();
                }
                return null;
            }
        };
    }

    @Override
    public Op undo(final int steps) {
        return new Op("undo") {
            @Override
            public Op run() {
                mInputConnection.beginBatchEdit();
                for (int i = 0; i < steps; i++) {
                    try {
                        Op op = mUndoStack.pop().run();
                        if (op == null) {
                            break;
                        }
                    } catch (NoSuchElementException ex) {
                        break;
                    }
                }
                mInputConnection.endBatchEdit();
                return Op.NO_OP;
            }
        };
    }

    /**
     * The combine operation modifies the stack by removing the top n elements and adding
     * their combination instead.
     */
    @Override
    public Op combine(final int steps) {
        return new Op("combine " + steps) {
            @Override
            public Op run() {
                final Deque<Op> combination = new ArrayDeque<>();
                try {
                    for (int i = 0; i < steps; i++) {
                        combination.push(mOpStack.pop());
                    }
                } catch (NoSuchElementException e) {
                    return null;
                }
                pushOp(new Op(combination.toString()) {
                    @Override
                    public Op run() {
                        while (!combination.isEmpty()) {
                            combination.pop().run();
                        }
                        // TODO: return correct combination of undos
                        return NO_OP;
                    }
                });
                return NO_OP;
            }
        };
    }

    @Override
    public Op apply(final int steps) {
        return new Op("apply") {
            @Override
            public Op run() {
                Op op = mOpStack.peek();
                if (op == null) {
                    return null;
                }
                final Deque<Op> combination = new ArrayDeque<>();
                mInputConnection.beginBatchEdit();
                for (int i = 0; i < steps; i++) {
                    Op undo = op.run();
                    if (undo == null) {
                        break;
                    }
                    combination.push(undo);
                }
                mInputConnection.endBatchEdit();
                return new Op("undo apply " + combination.size()) {

                    @Override
                    public Op run() {
                        mInputConnection.beginBatchEdit();
                        while (!combination.isEmpty()) {
                            combination.pop().run();
                        }
                        mInputConnection.endBatchEdit();
                        return NO_OP;
                    }
                };
            }
        };
    }

    /**
     * There is no undo, because the undo-stack does not survive the jump to another field.
     */
    @Override
    public Op goToPreviousField() {
        boolean success = mInputConnection.performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
        if (success) {
            return Op.NO_OP;
        }
        return null;
    }

    /**
     * There is no undo, because the undo-stack does not survive the jump to another field.
     */
    @Override
    public Op goToNextField() {
        boolean success = mInputConnection.performEditorAction(EditorInfo.IME_ACTION_NEXT);
        if (success) {
            return Op.NO_OP;
        }
        return null;
    }

    @Override
    public Op goToCharacterPosition(final int pos) {
        return new Op("goto " + pos) {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                ExtractedText et = getExtractedText();
                if (et != null) {
                    undo = getOpSetSelection(pos, pos, et.selectionStart, et.selectionEnd).run();
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }


    @Override
    public Op goForward(final int numberOfChars) {
        return move(numberOfChars);
    }

    @Override
    public Op goBackward(final int numberOfChars) {
        return move(-1 * numberOfChars);
    }

    @Override
    public Op goToEnd() {
        return new Op("goToEnd") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                ExtractedText et = getExtractedText();
                if (et != null && et.text != null) {
                    int pos = et.text.length();
                    undo = getOpSetSelection(pos, pos, et.selectionStart, et.selectionEnd).run();
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    /**
     * mInputConnection.performContextMenuAction(android.R.id.selectAll) does not create a selection
     */
    @Override
    public Op selectAll() {
        return new Op("selectAll") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                if (et != null) {
                    undo = getOpSetSelection(0, et.text.length(), et.selectionStart, et.selectionEnd).run();
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    // TODO: support undo
    @Override
    public Op cut() {
        boolean success = mInputConnection.performContextMenuAction(android.R.id.cut);
        if (success) {
            return Op.NO_OP;
        }
        return null;
    }

    // TODO: support undo
    @Override
    public Op cutAll() {
        mInputConnection.beginBatchEdit();
        Op op = selectAll();
        if (op != null) {
            cut();
        }
        mInputConnection.endBatchEdit();
        return op;
    }

    // TODO: support undo
    @Override
    public Op deleteAll() {
        mInputConnection.beginBatchEdit();
        Op op = selectAll();
        mInputConnection.commitText("", 0);
        mInputConnection.endBatchEdit();
        return op;
    }

    // TODO: support undo
    @Override
    public Op copy() {
        boolean success = mInputConnection.performContextMenuAction(android.R.id.copy);
        if (success) {
            return Op.NO_OP;
        }
        return null;
    }

    // TODO: support undo
    @Override
    public Op copyAll() {
        mInputConnection.beginBatchEdit();
        Op op = selectAll();
        if (op != null) {
            copy();
        }
        mInputConnection.endBatchEdit();
        return op;
    }

    // TODO: support undo
    @Override
    public Op paste() {
        if (mInputConnection.performContextMenuAction(android.R.id.paste)) {
            return Op.NO_OP;
        }
        return null;
    }

    @Override
    public Op resetSel() {
        return new Op("resetSel") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                if (et != null) {
                    undo = getOpSetSelection(et.selectionEnd, et.selectionEnd, et.selectionStart, et.selectionEnd).run();
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    /**
     * Deletes all characters up to the leftmost whitespace from the cursor (including the whitespace).
     * If something is selected then delete the selection.
     */
    @Override
    public Op deleteLeftWord() {
        return new Op("deleteLeftWord") {
            @Override
            public Op run() {
                Op undo = null;
                boolean success = false;
                mInputConnection.beginBatchEdit();
                // If something is selected then delete the selection and return
                final String oldText = getSelectedText();
                if (oldText.length() > 0) {
                    undo = getCommitTextOp(oldText, "").run();
                } else {
                    final CharSequence beforeCursor = mInputConnection.getTextBeforeCursor(MAX_DELETABLE_CONTEXT, 0);
                    if (beforeCursor != null) {
                        final int beforeCursorLength = beforeCursor.length();
                        Matcher m = WHITESPACE_AND_TOKEN.matcher(beforeCursor);
                        int lastIndex = 0;
                        while (m.find()) {
                            // If the cursor is immediately left from WHITESPACE_AND_TOKEN, then
                            // delete the WHITESPACE_AND_TOKEN, otherwise delete whatever is in between.
                            lastIndex = beforeCursorLength == m.end() ? m.start() : m.end();
                        }
                        if (lastIndex > 0) {
                            success = mInputConnection.deleteSurroundingText(beforeCursorLength - lastIndex, 0);
                        } else if (beforeCursorLength < MAX_DELETABLE_CONTEXT) {
                            success = mInputConnection.deleteSurroundingText(beforeCursorLength, 0);
                        }
                        if (success) {
                            mInputConnection.endBatchEdit();
                            final CharSequence cs = lastIndex > 0 ? beforeCursor.subSequence(lastIndex, beforeCursorLength) : beforeCursor;
                            undo = new Op("commitText: " + cs) {
                                @Override
                                public Op run() {
                                    if (mInputConnection.commitText(cs, 0)) {
                                        return NO_OP;
                                    }
                                    return null;
                                }
                            };
                        }
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op select(final String query) {
        return new Op("select " + query) {

            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                ExtractedText et = getExtractedText();
                if (et != null) {
                    Pair<Integer, CharSequence> queryResult = lastIndexOf(query, et);
                    if (queryResult.first >= 0) {
                        undo = getOpSetSelection(queryResult.first, queryResult.first + queryResult.second.length(), et.selectionStart, et.selectionEnd).run();
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op selectReBefore(final String regex) {
        return new Op("selectReBefore") {

            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                if (et != null) {
                    CharSequence input = et.text.subSequence(0, et.selectionStart);
                    Pair<Integer, Integer> pos = match(Pattern.compile(regex), input, false);
                    if (pos != null) {
                        undo = getOpSetSelection(pos.first, pos.second, et.selectionStart, et.selectionEnd).run();
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op delete(String str) {
        return replace(str, "");
    }

    @Override
    public Op replace(final String query, final String replacement) {
        return new Op("replace") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                if (et != null) {
                    Pair<Integer, CharSequence> queryResult = lastIndexOf(query, et);
                    final CharSequence match = queryResult.second;
                    if (queryResult.first >= 0) {
                        boolean success = mInputConnection.setSelection(queryResult.first, queryResult.first);
                        if (success) {
                            // Delete existing text
                            success = mInputConnection.deleteSurroundingText(0, match.length());
                            if (replacement.isEmpty()) {
                                if (success) {
                                    undo = new Op("undo replace1") {
                                        @Override
                                        public Op run() {
                                            mInputConnection.beginBatchEdit();
                                            boolean success2 = mInputConnection.commitText(match, 1) &&
                                                    mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                                            mInputConnection.endBatchEdit();
                                            if (success2) {
                                                return NO_OP;
                                            }
                                            return null;
                                        }
                                    };
                                }
                            } else {
                                success = mInputConnection.commitText(replacement, 1);
                                if (success) {
                                    undo = new Op("undo replace2") {
                                        @Override
                                        public Op run() {
                                            mInputConnection.beginBatchEdit();
                                            boolean success2 = mInputConnection.deleteSurroundingText(replacement.length(), 0) &&
                                                    mInputConnection.commitText(match, 1) &&
                                                    mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                                            mInputConnection.endBatchEdit();
                                            if (success2) {
                                                return NO_OP;
                                            }
                                            return null;
                                        }
                                    };
                                }
                            }
                        }
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op replaceSel(final String str) {
        return new Op("replaceSel") {
            @Override
            public Op run() {
                // Replace mentions of selection with a back-reference
                String out = SELREF.matcher(str).replaceAll("\\$1");
                mInputConnection.beginBatchEdit();
                // Change the current selection with the input argument, possibly embedding the selection.
                String oldText = getSelectedText();
                Op undo = getCommitTextOp(oldText, ALL.matcher(oldText).replaceAll(out)).run();
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op ucSel() {
        return new Op("ucSel") {
            @Override
            public Op run() {
                mInputConnection.beginBatchEdit();
                String oldText = getSelectedText();
                Op undo = getCommitTextOp(oldText, oldText.toUpperCase()).run();
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op lcSel() {
        return new Op("lcSel") {
            @Override
            public Op run() {
                mInputConnection.beginBatchEdit();
                String oldText = getSelectedText();
                Op undo = getCommitTextOp(oldText, oldText.toLowerCase()).run();
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op incSel() {
        return new Op("incSel") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                String oldText = getSelectedText();
                try {
                    undo = getCommitTextOp(oldText, String.valueOf(Integer.parseInt(oldText) + 1)).run();
                } catch (NumberFormatException e) {
                    // Intentional
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    // TODO: support undo
    @Override
    public Op keyCode(final int code) {
        return new Op("keyCode") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code))) {
                    return Op.NO_OP;
                }
                return null;
            }
        };
    }

    // TODO: support undo
    @Override
    public Op keyCodeStr(String symbolicName) {
        int code = KeyEvent.keyCodeFromString("KEYCODE_" + symbolicName);
        if (code != KeyEvent.KEYCODE_UNKNOWN) {
            if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code))) {
                return Op.NO_OP;
            }
        }
        return null;
    }

    @Override
    public Op imeActionDone() {
        // Does not work on Google Searchbar
        return getEditorActionOp(EditorInfo.IME_ACTION_DONE);
    }

    @Override
    public Op imeActionGo() {
        // Works in Google Searchbar, GF Translator, but NOT in the Firefox search widget
        return getEditorActionOp(EditorInfo.IME_ACTION_GO);
    }

    @Override
    public Op imeActionSearch() {
        return getEditorActionOp(EditorInfo.IME_ACTION_SEARCH);
    }

    @Override
    public Op imeActionSend() {
        return getEditorActionOp(EditorInfo.IME_ACTION_SEND);
    }

    // TODO: move undo, combine, apply, etc. out of this class because these are meta commands
    public void popOp() {
        mOpStack.pop();
    }

    public void pushOp(Op op) {
        mOpStack.push(op);
        Log.i("undo: push op: " + mOpStack.toString());
    }

    public void pushOpUndo(Op op) {
        mUndoStack.push(op);
        Log.i("undo: push undo: " + mUndoStack.toString());
    }

    @Override
    public Deque<Op> getOpStack() {
        return mOpStack;
    }

    @Override
    public Deque<Op> getUndoStack() {
        return mUndoStack;
    }

    private Op getEditorActionOp(final int editorAction) {
        return new Op("editorAction " + editorAction) {
            @Override
            public Op run() {
                if (mInputConnection.performEditorAction(editorAction)) {
                    return Op.NO_OP;
                }
                return null;
            }
        };
    }

    /**
     * Updates the text field, modifying only the parts that have changed.
     * Adds text at the cursor, possibly overwriting a selection.
     * Returns the number of characters added.
     */
    private int commitWithOverwrite(String text) {
        // Calculate the length of the text that has changed
        String commonPrefix = greatestCommonPrefix(mPrevText, text);
        int commonPrefixLength = commonPrefix.length();

        mInputConnection.beginBatchEdit();
        // Delete the part that changed compared to the partial text added earlier.
        int deletableLength = mPrevText.length() - commonPrefixLength;
        if (deletableLength > 0) {
            mInputConnection.deleteSurroundingText(deletableLength, 0);
        }

        // Finish if there is nothing to add
        if (text.isEmpty() || commonPrefixLength == text.length()) {
            mAddedLength -= deletableLength;
        } else {
            CharSequence leftContext = "";
            String glue = "";
            // If the prev text and the current text share no prefix then recalculate the glue.
            if (commonPrefixLength == 0) {
                // We look at the left context of the cursor
                // to decide which glue symbol to use and whether to capitalize the text.
                CharSequence textBeforeCursor = mInputConnection.getTextBeforeCursor(MAX_DELETABLE_CONTEXT, 0);
                // In some error situations, null is returned
                if (textBeforeCursor != null) {
                    leftContext = textBeforeCursor;
                }
                glue = getGlue(text, leftContext);
                mAddedLength = glue.length() + text.length();
            } else {
                text = text.substring(commonPrefixLength);
                leftContext = commonPrefix;
                mAddedLength = mAddedLength - deletableLength + text.length();
            }
            text = capitalizeIfNeeded(text, leftContext);
            mInputConnection.commitText(glue + text, 1);
        }
        mInputConnection.endBatchEdit();
        return mAddedLength;
    }

    /**
     * Op that commits a text at the cursor. If successful then an undo is returned which deletes
     * the text and restores the old selection.
     */
    private Op getCommitTextOp(final CharSequence oldText, final CharSequence newText) {
        return new Op("commitText") {
            @Override
            public Op run() {
                Op undo = null;
                final ExtractedText et = getExtractedText();
                if (mInputConnection.commitText(newText, 1)) {
                    undo = new Op("deleteSurroundingText+commitText") {
                        @Override
                        public Op run() {
                            mInputConnection.beginBatchEdit();
                            boolean success = mInputConnection.deleteSurroundingText(newText.length(), 0);
                            if (success && oldText != null) {
                                success = mInputConnection.commitText(oldText, 1);
                            }
                            if (success) {
                                success = mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                            }
                            mInputConnection.endBatchEdit();
                            if (success) {
                                return NO_OP;
                            }
                            return null;
                        }
                    };
                }
                return undo;
            }
        };
    }

    private String rewrite(String str) {
        if (mUtteranceRewriter == null) {
            return str;
        }
        UtteranceRewriter.Rewrite triple = mUtteranceRewriter.getRewrite(str);
        return triple.mStr;
    }

    private Op getOpSetSelection(final int i, final int j, final int oldSelectionStart, final int oldSelectionEnd) {
        return new Op("setSelection") {
            @Override
            public Op run() {
                Op undo = null;
                if (mInputConnection.setSelection(i, j)) {
                    undo = new Op("setSelection") {
                        @Override
                        public Op run() {
                            if (mInputConnection.setSelection(oldSelectionStart, oldSelectionEnd)) {
                                return NO_OP;
                            }
                            return null;
                        }
                    };
                }
                return undo;
            }
        };
    }


    /**
     * Tries to match a substring before the cursor, using case-insensitive matching.
     * TODO: this might not work with some Unicode characters
     *
     * @param query search string
     * @param et    text to search from
     * @return pair index of the last occurrence of the match, and the matched string
     */
    private Pair<Integer, CharSequence> lastIndexOf(String query, ExtractedText et) {
        int start = et.selectionStart;
        query = query.toLowerCase();
        CharSequence input = et.text.subSequence(0, start);
        CharSequence match = null;
        int index = input.toString().toLowerCase().lastIndexOf(query);
        if (index >= 0) {
            match = input.subSequence(index, index + query.length());
        }
        return new Pair<>(index, match);
    }

    /**
     * Go to the first/last match and return the indices of the 1st group in the match if available.
     * If not then return the indices of the whole match.
     * If no match was found then return {@code null}.
     */
    private Pair<Integer, Integer> match(Pattern pattern, CharSequence input, boolean matchFirst) {
        Matcher matcher = pattern.matcher(input);
        Pair<Integer, Integer> pos = null;
        while (matcher.find()) {
            int group = 0;
            if (matcher.groupCount() > 0) {
                group = 1;
            }
            pos = new Pair<>(matcher.start(group), matcher.end(group));
            if (matchFirst) {
                return pos;
            }
        }
        return pos;
    }

    private String getSelectedText() {
        CharSequence cs = mInputConnection.getSelectedText(0);
        if (cs == null || cs.length() == 0) {
            return "";
        }
        return cs.toString();
    }

    private ExtractedText getExtractedText() {
        return mInputConnection.getExtractedText(new ExtractedTextRequest(), 0);
    }

    /**
     * Move either left (negative number of steps) or right (positive num of steps)
     */
    private Op move(final int numberOfChars) {
        return new Op("move") {
            @Override
            public Op run() {
                Op undoOp = null;
                mInputConnection.beginBatchEdit();
                ExtractedText extractedText = getExtractedText();
                if (extractedText != null) {
                    int pos;
                    if (numberOfChars < 0) {
                        pos = extractedText.selectionStart;
                    } else {
                        pos = extractedText.selectionEnd;
                    }
                    int newPos = pos + numberOfChars;
                    undoOp = getOpSetSelection(newPos, newPos, extractedText.selectionStart, extractedText.selectionEnd).run();
                }
                mInputConnection.endBatchEdit();
                return undoOp;
            }
        };
    }

    // TODO: not sure we need this
    private Op push(final Op op) {
        return new Op("push") {
            @Override
            public Op run() {
                mOpStack.push(op);
                return new Op("pop") {
                    @Override
                    public Op run() {
                        mOpStack.pop();
                        return push(op);
                    }
                };
            }
        };
    }

    /**
     * Check the last committed texts if they can be combined into a command. If so, then undo
     * these commits and return the constructed command.
     */
    private UtteranceRewriter.Rewrite applyCommand(String text) {
        int len = mCommandPrefix.size();
        for (int i = Math.min(MAX_UTT_IN_COMMAND, len); i > 0; i--) {
            List sublist = mCommandPrefix.subList(len - i, len);
            // TODO: sometimes sublist is empty?
            String possibleCommand = TextUtils.join(" ", sublist);
            if (possibleCommand.isEmpty()) {
                possibleCommand = text;
            } else {
                possibleCommand += " " + text;
            }
            Log.i("applyCommand: testing: <" + possibleCommand + ">");
            UtteranceRewriter.Rewrite rewrite = mUtteranceRewriter.getRewrite(possibleCommand);
            if (rewrite.isCommand()) {
                Log.i("applyCommand: isCommand: " + possibleCommand);
                undo(i);
                return rewrite;
            }
        }
        return mUtteranceRewriter.getRewrite(text);
    }

    /**
     * Capitalize if required by left context
     */
    private static String capitalizeIfNeeded(String text, CharSequence leftContext) {
        // Capitalize if required by left context
        String leftContextTrimmed = leftContext.toString().trim();
        if (leftContextTrimmed.length() == 0
                || Constants.CHARACTERS_EOS.contains(leftContextTrimmed.charAt(leftContextTrimmed.length() - 1))) {
            // Since the text can start with whitespace (newline),
            // we capitalize the first non-whitespace character.
            int firstNonWhitespaceIndex = -1;
            for (int i = 0; i < text.length(); i++) {
                if (!Constants.CHARACTERS_WS.contains(text.charAt(i))) {
                    firstNonWhitespaceIndex = i;
                    break;
                }
            }
            if (firstNonWhitespaceIndex > -1) {
                String newText = text.substring(0, firstNonWhitespaceIndex)
                        + Character.toUpperCase(text.charAt(firstNonWhitespaceIndex));
                if (firstNonWhitespaceIndex < text.length() - 1) {
                    newText += text.substring(firstNonWhitespaceIndex + 1);
                }
                return newText;
            }
        }
        return text;
    }

    /**
     * Return a whitespace iff the 1st character of the text is not punctuation, or whitespace, etc.
     */
    private static String getGlue(String text, CharSequence leftContext) {
        char firstChar = text.charAt(0);

        // TODO: experimental: glue all 1-character strings (somewhat Estonian-specific)
        if (text.length() == 1 && Character.isLetter(firstChar)) {
            return "";
        }

        if (leftContext.length() == 0
                || Constants.CHARACTERS_WS.contains(firstChar)
                || Constants.CHARACTERS_PUNCT.contains(firstChar)) {
            return "";
        }

        char prevChar = leftContext.charAt(leftContext.length() - 1);
        if (Constants.CHARACTERS_WS.contains(prevChar)
                || Constants.CHARACTERS_STICKY.contains(prevChar)) {
            return "";
        }
        return " ";
    }

    private static String greatestCommonPrefix(String a, String b) {
        int minLength = Math.min(a.length(), b.length());
        for (int i = 0; i < minLength; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return a.substring(0, i);
            }
        }
        return a.substring(0, minLength);
    }
}
