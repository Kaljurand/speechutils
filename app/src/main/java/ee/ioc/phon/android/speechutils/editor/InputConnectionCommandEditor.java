package ee.ioc.phon.android.speechutils.editor;

import android.text.TextUtils;
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
 */
public class InputConnectionCommandEditor implements CommandEditor {

    public abstract class Op {
        private final String mId;

        public Op(String id) {
            mId = id;
        }

        public String toString() {
            return mId;
        }

        abstract public boolean execute();
    }

    // Maximum number of previous utterances that a command can contain
    private static final int MAX_UTT_IN_COMMAND = 3;

    // Maximum number of characters that left-swipe is willing to delete
    private static final int MAX_DELETABLE_CONTEXT = 100;
    // Token optionally preceded by whitespace
    private static final Pattern WHITESPACE_AND_TOKEN = Pattern.compile("\\s*\\w+");
    private static final Pattern SELREF = Pattern.compile("\\{\\}");
    private static final Pattern ALL = Pattern.compile("^(.*)$");

    private String mPrevText = "";

    // TODO: Restrict the size of these stacks

    // The command prefix is a list of consecutive final results whose concatenation can possibly
    // form a command. An item is added to the list for every final result that is not a command.
    // The list if cleared if a command is executed or if reset() is called.
    private List<String> mCommandPrefix = new ArrayList<>();
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
                push(new Op("delete " + len) {
                    @Override
                    public boolean execute() {
                        mInputConnection.beginBatchEdit();
                        boolean success = mInputConnection.deleteSurroundingText(len, 0);
                        if (et != null && selectedText.length() > 0) {
                            success = mInputConnection.commitText(selectedText, 1) &&
                                    mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                        }
                        mInputConnection.endBatchEdit();
                        return success;
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
        int lengthWritten = commitWithOverwrite(textRewritten);
        if (lengthWritten > 0) {
            mPrevText = textRewritten;
        }
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
    public boolean goUp() {
        return goUp(true);
    }

    @Override
    public boolean goDown() {
        return goDown(true);
    }

    @Override
    public boolean goLeft() {
        return goLeft(true);
    }

    @Override
    public boolean goRight() {
        return goRight(true);
    }

    @Override
    public boolean undo() {
        return undo(1);
    }

    @Override
    public boolean undo(int steps) {
        mInputConnection.beginBatchEdit();
        for (int i = 0; i < steps; i++) {
            try {
                if (!mUndoStack.pop().execute()) {
                    return false;
                }
            } catch (NoSuchElementException ex) {
                break;
            }
        }
        mInputConnection.endBatchEdit();
        return true;
    }

    /**
     * There is no undo, because the undo-stack does not survive the jump to another field.
     */
    @Override
    public boolean goToPreviousField() {
        return mInputConnection.performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
    }

    /**
     * There is no undo, because the undo-stack does not survive the jump to another field.
     */
    @Override
    public boolean goToNextField() {
        return mInputConnection.performEditorAction(EditorInfo.IME_ACTION_NEXT);
    }

    @Override
    public boolean goToCharacterPosition(int pos) {
        return setSelection(pos, pos);
    }


    @Override
    public boolean goForward(final int numberOfChars) {
        return move(numberOfChars, true);
    }

    @Override
    public boolean goBackward(final int numberOfChars) {
        return move(-1 * numberOfChars, true);
    }

    @Override
    public boolean goToEnd() {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        ExtractedText et = getExtractedText();
        if (et != null && et.text != null) {
            int pos = et.text.length();
            success = setSelection(pos, pos, et.selectionStart, et.selectionEnd);
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    /**
     * mInputConnection.performContextMenuAction(android.R.id.selectAll) does not create a selection
     */
    @Override
    public boolean selectAll() {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        final ExtractedText et = getExtractedText();
        if (et != null) {
            success = setSelection(0, et.text.length(), et.selectionStart, et.selectionEnd);
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    // TODO: support undo
    @Override
    public boolean cut() {
        return mInputConnection.performContextMenuAction(android.R.id.cut);
    }

    // TODO: support undo
    @Override
    public boolean cutAll() {
        mInputConnection.beginBatchEdit();
        boolean success = selectAll() && cut();
        mInputConnection.endBatchEdit();
        return success;
    }

    // TODO: support undo
    @Override
    public boolean deleteAll() {
        mInputConnection.beginBatchEdit();
        boolean success = selectAll() && mInputConnection.commitText("", 0);
        mInputConnection.endBatchEdit();
        return success;
    }

    // TODO: support undo
    @Override
    public boolean copy() {
        return mInputConnection.performContextMenuAction(android.R.id.copy);
    }

    // TODO: support undo
    @Override
    public boolean copyAll() {
        mInputConnection.beginBatchEdit();
        boolean success = selectAll() && copy();
        mInputConnection.endBatchEdit();
        return success;
    }

    // TODO: support undo
    @Override
    public boolean paste() {
        return mInputConnection.performContextMenuAction(android.R.id.paste);
    }

    @Override
    public boolean addSpace() {
        return addText(" ");
    }

    @Override
    public boolean addNewline() {
        return addText("\n");
    }

    @Override
    public boolean resetSel() {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        final ExtractedText et = getExtractedText();
        if (et != null) {
            success = setSelection(et.selectionEnd, et.selectionEnd, et.selectionStart, et.selectionEnd);
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    /**
     * Deletes all characters up to the leftmost whitespace from the cursor (including the whitespace).
     * If something is selected then delete the selection.
     */
    @Override
    public boolean deleteLeftWord() {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        // If something is selected then delete the selection and return
        String oldText = getSelectedText();
        if (oldText.length() > 0) {
            success = commitText(oldText, "");
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
                    final CharSequence cs = lastIndex > 0 ? beforeCursor.subSequence(lastIndex, beforeCursorLength) : beforeCursor;
                    push(new Op("commitText: " + cs) {
                        @Override
                        public boolean execute() {
                            return mInputConnection.commitText(cs, 0);
                        }
                    });

                }
            }
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean select(String str) {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        final ExtractedText et = getExtractedText();
        if (et != null) {
            int index = lastIndexOf(str, et);
            if (index > -1) {
                success = setSelection(index, index + str.length(), et.selectionStart, et.selectionEnd);
            }
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean delete(String str) {
        return replace(str, "");
    }

    @Override
    public boolean replace(final String str1, final String str2) {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        final ExtractedText et = getExtractedText();
        if (et != null) {
            int index = lastIndexOf(str1, et);
            if (index >= 0) {
                success = mInputConnection.setSelection(index, index);
                if (success) {
                    success = mInputConnection.deleteSurroundingText(0, str1.length());
                    if (str2.isEmpty()) {
                        if (success) {
                            push(new Op("undo replace1") {
                                @Override
                                public boolean execute() {
                                    mInputConnection.beginBatchEdit();
                                    boolean success2 = mInputConnection.commitText(str1, 1) &&
                                            mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                                    mInputConnection.endBatchEdit();
                                    return success2;
                                }
                            });
                        }
                    } else {
                        success = mInputConnection.commitText(str2, 1);
                        if (success) {
                            push(new Op("undo replace2") {
                                @Override
                                public boolean execute() {
                                    mInputConnection.beginBatchEdit();
                                    boolean success2 = mInputConnection.deleteSurroundingText(str2.length(), 0) &&
                                            mInputConnection.commitText(str1, 1) &&
                                            mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                                    mInputConnection.endBatchEdit();
                                    return success2;
                                }
                            });
                        }
                    }
                }
            }
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean replaceSel(String str) {
        boolean success;
        // Replace mentions of selection with a back-reference
        String out = SELREF.matcher(str).replaceAll("\\$1");
        mInputConnection.beginBatchEdit();
        // Change the current selection with the input argument, possibly embedding the selection.
        String oldText = getSelectedText();
        success = commitText(oldText, ALL.matcher(oldText).replaceAll(out));
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean ucSel() {
        boolean success;
        mInputConnection.beginBatchEdit();
        String oldText = getSelectedText();
        success = commitText(oldText, oldText.toUpperCase());
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean lcSel() {
        boolean success;
        mInputConnection.beginBatchEdit();
        String oldText = getSelectedText();
        success = commitText(oldText, oldText.toLowerCase());
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean incSel() {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        String oldText = getSelectedText();
        try {
            success = commitText(oldText, String.valueOf(Integer.parseInt(oldText) + 1));
        } catch (NumberFormatException e) {
            // Intentional
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean imeActionDone() {
        // Does not work on Google Searchbar
        return mInputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE);
    }

    @Override
    public boolean imeActionGo() {
        // Works in Google Searchbar, GF Translator, but NOT in the Firefox search widget
        return mInputConnection.performEditorAction(EditorInfo.IME_ACTION_GO);
    }

    @Override
    public boolean imeActionSearch() {
        return mInputConnection.performEditorAction(EditorInfo.IME_ACTION_SEARCH);
    }

    @Override
    public boolean imeActionSend() {
        return mInputConnection.performEditorAction(EditorInfo.IME_ACTION_SEND);
    }

    /**
     * Updates the text field, modifying only the parts that have changed.
     * Adds text at the cursor, possibly overwriting a selection.
     * Returns true if text was added.
     */
    private int commitWithOverwrite(String text) {
        mInputConnection.beginBatchEdit();
        // Calculate the length of the text that has changed
        String commonPrefix = greatestCommonPrefix(mPrevText, text);
        int commonPrefixLength = commonPrefix.length();
        int prevLength = mPrevText.length();
        int deletableLength = prevLength - commonPrefixLength;

        if (deletableLength > 0) {
            mInputConnection.deleteSurroundingText(deletableLength, 0);
        }

        if (text.isEmpty() || commonPrefixLength == text.length()) {
            mInputConnection.endBatchEdit();
            return text.length();
        }

        // We look at the left context of the cursor
        // to decide which glue symbol to use and whether to capitalize the text.
        CharSequence leftContext = mInputConnection.getTextBeforeCursor(MAX_DELETABLE_CONTEXT, 0);
        // In some error situation, null is returned
        if (leftContext == null) {
            leftContext = "";
        }
        String glue = "";
        if (commonPrefixLength == 0) {
            glue = getGlue(text, leftContext);
        } else {
            text = text.substring(commonPrefixLength);
        }

        text = capitalizeIfNeeded(text, leftContext);
        mInputConnection.commitText(glue + text, 1);
        mInputConnection.endBatchEdit();
        return glue.length() + text.length();
    }

    @Override
    public String getUndoStack() {
        return mUndoStack.toString();
    }

    private boolean addText(final CharSequence text) {
        return commitText(null, text);
    }

    private boolean commitText(final CharSequence oldText, final CharSequence newText) {
        final ExtractedText et = getExtractedText();
        boolean success = mInputConnection.commitText(newText, 1);
        if (success) {
            push(new Op("deleteSurroundingText+commitText") {
                @Override
                public boolean execute() {
                    mInputConnection.beginBatchEdit();
                    boolean success2 = mInputConnection.deleteSurroundingText(newText.length(), 0);
                    if (success2 && oldText != null) {
                        success2 = mInputConnection.commitText(oldText, 1);
                    }
                    if (success2) {
                        success2 = mInputConnection.setSelection(et.selectionStart, et.selectionEnd);
                    }
                    mInputConnection.endBatchEdit();
                    return success2;
                }
            });
        }
        return success;
    }

    private String rewrite(String str) {
        if (mUtteranceRewriter == null) {
            return str;
        }
        UtteranceRewriter.Rewrite triple = mUtteranceRewriter.getRewrite(str);
        return triple.mStr;
    }

    private boolean setSelection(int i, int j, final int oldSelectionStart, final int oldSelectionEnd) {
        boolean success = mInputConnection.setSelection(i, j);
        if (success) {
            push(new Op("setSelection") {
                @Override
                public boolean execute() {
                    return mInputConnection.setSelection(oldSelectionStart, oldSelectionEnd);
                }
            });
        }
        return success;
    }

    /**
     * Using case-insensitive matching.
     * TODO: this might not work with some Unicode characters
     *
     * @param str search string
     * @param et  texts to search from
     * @return index of the last occurrence of the given string
     */
    private int lastIndexOf(String str, ExtractedText et) {
        int start = et.selectionStart;
        //int end = extractedText.selectionEnd;
        CharSequence allText = et.text;
        return allText.subSequence(0, start).toString().toLowerCase().lastIndexOf(str.toLowerCase());
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

    private boolean goUp(boolean undo) {
        boolean success = mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
        if (success && undo) {
            push(new Op("goDown") {
                @Override
                public boolean execute() {
                    return goDown(false);
                }
            });
        }
        return success;
    }

    private boolean goDown(boolean undo) {
        boolean success = mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
        if (success && undo) {
            push(new Op("goUp") {
                @Override
                public boolean execute() {
                    return goUp(false);
                }
            });
        }
        return success;
    }

    private boolean goLeft(boolean undo) {
        boolean success = mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
        if (success && undo) {
            push(new Op("goRight") {
                @Override
                public boolean execute() {
                    return goRight(false);
                }
            });
        }
        return success;
    }

    private boolean goRight(boolean undo) {
        boolean success = mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        if (success && undo) {
            push(new Op("goLeft") {
                @Override
                public boolean execute() {
                    return goLeft(false);
                }
            });
        }
        return success;
    }

    private boolean setSelection(int i, int j) {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        ExtractedText et = getExtractedText();
        if (et != null) {
            success = setSelection(i, j, et.selectionStart, et.selectionEnd);
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    /**
     * Move either left (negative number of steps) or right (positive num of steps)
     */
    private boolean move(final int numberOfChars, boolean undo) {
        boolean success = false;
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
            success = mInputConnection.setSelection(newPos, newPos);
            if (success && undo) {
                push(new Op("move") {
                    @Override
                    public boolean execute() {
                        return move(-1 * numberOfChars, false);
                    }
                });
            }
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    private void push(Op op) {
        mUndoStack.push(op);
        Log.i("undo: push: " + mUndoStack.toString());
    }

    /**
     * Check the last committed texts if they can be combined into a command. If so, then undo
     * these commits and return the constructed command.
     */
    private UtteranceRewriter.Rewrite applyCommand(String text) {
        int len = mCommandPrefix.size();
        for (int i = Math.min(MAX_UTT_IN_COMMAND, len); i > 0; i--) {
            List sublist = mCommandPrefix.subList(len - i, len);
            String possibleCommand = TextUtils.join(" ", sublist) + " " + text;
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

    private static String getGlue(String text, CharSequence leftContext) {
        char firstChar = text.charAt(0);

        if (leftContext.length() == 0
                || Constants.CHARACTERS_WS.contains(firstChar)
                || Constants.CHARACTERS_PUNCT.contains(firstChar)
                || Constants.CHARACTERS_WS.contains(leftContext.charAt(leftContext.length() - 1))) {
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
