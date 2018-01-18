package ee.ioc.phon.android.speechutils.editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ee.ioc.phon.android.speechutils.Log;
import ee.ioc.phon.android.speechutils.R;
import ee.ioc.phon.android.speechutils.utils.HttpUtils;
import ee.ioc.phon.android.speechutils.utils.IntentUtils;
import ee.ioc.phon.android.speechutils.utils.JsonUtils;
import ee.ioc.phon.android.speechutils.utils.PreferenceUtils;

/**
 * TODO: this is work in progress
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
    private static final String F_SELECTION = "@sel()";

    private Context mContext;
    private SharedPreferences mPreferences;
    private Resources mRes;

    private String mPrevText = "";
    private int mAddedLength = 0;

    // TODO: Restrict the size of these stacks

    // The command prefix is a list of consecutive final results whose concatenation can possibly
    // form a command. An item is added to the list for every final result that is not a command.
    // The list if cleared if a command is executed.
    private List<String> mCommandPrefix = new ArrayList<>();
    private Deque<Op> mOpStack = new ArrayDeque<>();
    private Deque<Op> mUndoStack = new ArrayDeque<>();

    private InputConnection mInputConnection;

    private List<UtteranceRewriter> mRewriters;

    public InputConnectionCommandEditor(Context context) {
        mContext = context;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mRes = context.getResources();
    }

    public void setInputConnection(InputConnection inputConnection) {
        mInputConnection = inputConnection;
    }

    protected InputConnection getInputConnection() {
        return mInputConnection;
    }

    @Override
    public void setRewriters(List<UtteranceRewriter> urs) {
        mRewriters = urs;
        reset();
    }

    @Override
    public boolean runOp(Op op) {
        return runOp(op, true);
    }

    @Override
    public boolean runOp(Op op, boolean undoable) {
        // TODO: why does this happen
        //if (op == null) {
        //    return false;
        //}
        reset();
        Op undo = op.run();
        if (undo == null) {
            // Operation failed;
            return false;
        }
        if (undoable && !undo.isNoOp()) {
            pushOp(op);
            pushOpUndo(undo);
        }
        return true;
    }

    @Override
    public Op getOpFromText(final String text) {
        List<Op> ops = new ArrayList<>();
        boolean isCommand = false;
        String newText = text;
        for (UtteranceRewriter ur : mRewriters) {
            if (ur == null) {
                continue;
            }
            UtteranceRewriter.Rewrite rewrite = ur.getRewrite(text);
            newText = rewrite.mStr;
            if (rewrite.isCommand()) {
                isCommand = true;
                ops.add(getCommitWithOverwriteOp(newText));
                CommandEditorManager.EditorCommand ec = CommandEditorManager.get(rewrite.mId);
                if (ec != null) {
                    ops.add(ec.getOp(this, rewrite.mArgs));
                }
                break;
            }
        }
        if (!isCommand) {
            ops.add(getCommitWithOverwriteOp(newText));
        }
        return combineOps(ops);
    }

    @Override
    public CommandEditorResult commitFinalResult(final String text) {
        CommandEditorResult result = null;
        if (mRewriters == null || mRewriters.isEmpty()) {
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
                        boolean success = deleteSurrounding(len, 0);
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
                CommandEditorManager.EditorCommand ec = CommandEditorManager.get(rewrite.mId);
                if (ec != null) {
                    // TODO: dont call runOp from here
                    success = runOp(ec.getOp(this, rewrite.mArgs));
                }
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

        String newText = text;
        if (mRewriters != null && !mRewriters.isEmpty()) {
            for (UtteranceRewriter ur : mRewriters) {
                if (ur == null) {
                    continue;
                }
                UtteranceRewriter.Rewrite rewrite = ur.getRewrite(newText);
                newText = rewrite.mStr;
                if (rewrite.isCommand()) {
                    break;
                }
            }
        }

        commitWithOverwrite(newText);
        mPrevText = newText;
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
    public void reset() {
        mCommandPrefix.clear();
        mPrevText = "";
        mAddedLength = 0;
    }

    @Override
    public Op activity(final String json) {
        return new Op("activity") {
            @Override
            public Op run() {
                Op undo = null;
                try {
                    if (IntentUtils.startActivityIfAvailable(mContext, JsonUtils.createIntent(json.replace(F_SELECTION, getSelectedText())))) {
                        undo = NO_OP;
                    }
                } catch (JSONException e) {
                    Log.i("startSearchActivity: JSON: " + e.getMessage());
                }
                return undo;
            }
        };
    }

    @Override
    public Op getUrl(final String url) {
        return new Op("getUrl") {
            @Override
            public Op run() {
                new AsyncTask<String, Void, String>() {

                    @Override
                    protected String doInBackground(String... urls) {
                        try {
                            return HttpUtils.getUrl(urls[0]);
                        } catch (IOException e) {
                            return "[ERROR: Unable to retrieve " + urls[0] + ": " + e.getLocalizedMessage() + "]";
                        }
                    }

                    @Override
                    protected void onPostExecute(String result) {
                        runOp(replaceSel(result));
                    }
                }.execute(url);
                return Op.NO_OP;
            }
        };
    }

    @Override
    public Op keyUp() {
        return new Op("goUp") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))) {
                    return keyDown();
                }
                return null;
            }
        };
    }

    @Override
    public Op keyDown() {
        return new Op("goDown") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))) {
                    return keyUp();
                }
                return null;
            }
        };
    }

    @Override
    public Op keyLeft() {
        return new Op("goLeft") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))) {
                    return keyRight();
                }
                return null;
            }
        };
    }

    @Override
    public Op keyRight() {
        return new Op("goRight") {
            @Override
            public Op run() {
                if (mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))) {
                    return keyLeft();
                }
                return null;
            }
        };
    }

    /**
     * Returns an operation that pops the undo stack the given number of times,
     * executing each popped op. If the stack does not contain the given number of elements, or
     * one of the ops fails, then returns null. Otherwise returns NO_OP.
     */
    @Override
    public Op undo(final int steps) {
        return new Op("undo") {
            @Override
            public Op run() {
                Op undo = Op.NO_OP;
                mInputConnection.beginBatchEdit();
                for (int i = 0; i < steps; i++) {
                    try {
                        Op op = mUndoStack.pop().run();
                        if (op == null) {
                            undo = null;
                            break;
                        }
                    } catch (NoSuchElementException ex) {
                        undo = null;
                        break;
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    /**
     * The combine operation modifies the stack by removing the top n elements and adding
     * their combination instead.
     * TODO: implement undo
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
                mOpStack.push(combineOps(combination));
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
                return new Op("undo apply", combination.size()) {

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

    @Override
    public Op moveAbs(final int pos) {
        return new Op("moveAbs " + pos) {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                ExtractedText et = getExtractedText();
                if (et != null) {
                    int charPos = pos;
                    if (pos < 0) {
                        //-1 == end of text
                        charPos = et.text.length() + pos + 1;
                    }
                    undo = getOpSetSelection(charPos, charPos, et.selectionStart, et.selectionEnd).run();
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op cut() {
        return getContextMenuActionOp(android.R.id.cut);
    }

    @Override
    public Op copy() {
        return getContextMenuActionOp(android.R.id.copy);
    }

    @Override
    public Op paste() {
        return getContextMenuActionOp(android.R.id.paste);
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

    // Not undoable
    @Override
    public Op cutAll() {
        Collection<Op> collection = new ArrayList<>();
        collection.add(selectAll());
        collection.add(cut());
        return combineOps(collection);
    }

    // Not undoable
    @Override
    public Op deleteAll() {
        return new Op("deleteAll") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                Op op = selectAll().run();
                if (op != null) {
                    if (mInputConnection.commitText("", 0)) {
                        undo = NO_OP;
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    // TODO: test with failing ops
    @Override
    public Op combineOps(final Collection<Op> ops) {
        return new Op(ops.toString(), ops.size()) {
            @Override
            public Op run() {
                final Deque<Op> combination = new ArrayDeque<>();
                mInputConnection.beginBatchEdit();
                for (Op op : ops) {
                    Op undo = op.run();
                    if (undo == null) {
                        break;
                    }
                    combination.push(undo);
                }
                mInputConnection.endBatchEdit();
                return new Op(combination.toString(), combination.size()) {
                    @Override
                    public Op run() {
                        mInputConnection.beginBatchEdit();
                        while (!combination.isEmpty()) {
                            Op undo1 = combination.pop().run();
                            if (undo1 == null) {
                                break;
                            }
                        }
                        mInputConnection.endBatchEdit();
                        return combineOps(ops);
                    }
                };
            }
        };
    }

    // Not undoable
    @Override
    public Op copyAll() {
        Collection<Op> collection = new ArrayList<>();
        collection.add(selectAll());
        collection.add(copy());
        return combineOps(collection);
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
                            success = deleteSurrounding(beforeCursorLength - lastIndex, 0);
                        } else if (beforeCursorLength < MAX_DELETABLE_CONTEXT) {
                            success = deleteSurrounding(beforeCursorLength, 0);
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
                    Pair<Integer, CharSequence> queryResult = lastIndexOf(query.replace(F_SELECTION, getSelectedText()), et);
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
                    // 0 == last match
                    Pair<Integer, Integer> pos = matchNth(Pattern.compile(regex), input, 0);
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
    public Op selectReAfter(final String regex, final int n) {
        return new Op("selectReAfter") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                if (et != null) {
                    CharSequence input = et.text.subSequence(et.selectionEnd, et.text.length());
                    Pair<Integer, Integer> pos = matchNth(Pattern.compile(regex), input, n);
                    if (pos != null) {
                        undo = getOpSetSelection(et.selectionEnd + pos.first, et.selectionEnd + pos.second, et.selectionStart, et.selectionEnd).run();
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op selectRe(final String regex, final boolean applyToSelection) {
        return new Op("selectRe") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                if (et != null) {
                    if (applyToSelection || et.selectionStart == et.selectionEnd) {
                        Pair<Integer, Integer> pos = matchAtPos(Pattern.compile(regex), et.text, et.selectionStart, et.selectionEnd);
                        if (pos != null) {
                            undo = getOpSetSelection(pos.first, pos.second, et.selectionStart, et.selectionEnd).run();
                        }
                    }
                }
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
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
                            success = deleteSurrounding(0, match.length());
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
                                            boolean success2 = deleteSurrounding(replacement.length(), 0) &&
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
                mInputConnection.beginBatchEdit();
                // Change the current selection with the input argument, possibly embedding the selection.
                String selectedText = getSelectedText();
                Op undo = getCommitTextOp(selectedText, str.replace(F_SELECTION, selectedText)).run();
                mInputConnection.endBatchEdit();
                return undo;
            }
        };
    }

    @Override
    public Op replaceSelRe(final String regex, final String repl) {
        return new Op("replaceSelRe") {
            @Override
            public Op run() {
                mInputConnection.beginBatchEdit();
                String selectedText = getSelectedText();
                String newText = selectedText.replaceAll(regex, repl);
                Op undo = getCommitTextOp(selectedText, newText).run();
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

    @Override
    public Op saveClip(final String key, final String val) {
        return new Op("saveClip " + key) {
            @Override
            public Op run() {
                PreferenceUtils.putPrefMapEntry(mPreferences, mRes, R.string.keyClipboardMap, key, val.replace(F_SELECTION, getSelectedText()));
                return Op.NO_OP;
            }
        };
    }

    @Override
    public Op loadClip(final String key) {
        return new Op("loadClip " + key) {
            @Override
            public Op run() {
                Op undo = null;
                String savedText = PreferenceUtils.getPrefMapEntry(mPreferences, mRes, R.string.keyClipboardMap, key);
                if (savedText != null) {
                    mInputConnection.beginBatchEdit();
                    undo = getCommitTextOp(getSelectedText(), savedText).run();
                    mInputConnection.endBatchEdit();
                }
                return undo;
            }
        };
    }

    @Override
    public Op showClipboard() {
        return new Op("showClipboard") {
            @Override
            public Op run() {
                Op undo = null;
                Map<String, String> clipboard = PreferenceUtils.getPrefMap(mPreferences, mRes, R.string.keyClipboardMap);
                if (clipboard != null && !clipboard.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry entry : clipboard.entrySet()) {
                        sb.append('<');
                        sb.append(entry.getKey());
                        sb.append('|');
                        sb.append(entry.getValue());
                        sb.append('>');
                        sb.append('\n');
                    }
                    mInputConnection.beginBatchEdit();
                    undo = getCommitTextOp(getSelectedText(), sb.toString()).run();
                    mInputConnection.endBatchEdit();
                }
                return undo;
            }
        };
    }

    @Override
    public Op clearClipboard() {
        return new Op("clearClipboard") {
            @Override
            public Op run() {
                PreferenceUtils.clearPrefMap(mPreferences, mRes, R.string.keyClipboardMap);
                return Op.NO_OP;
            }
        };
    }

    // TODO: share code with deleteLeftWord
    @Override
    public Op deleteLeftChars(final int numOfChars) {
        return new Op("deleteLeftChars") {
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
                    final CharSequence cs = mInputConnection.getTextBeforeCursor(numOfChars, 0);
                    if (cs != null) {
                        success = deleteSurrounding(numOfChars, 0);
                        if (success) {
                            mInputConnection.endBatchEdit();
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

    /**
     * Not undoable
     *
     * @param code key code
     * @return op that sends the given code
     */
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

    /**
     * Not undoable
     *
     * @param symbolicName key code's symbolic name
     * @return op that sends the given code
     */
    @Override
    public Op keyCodeStr(String symbolicName) {
        int code = KeyEvent.keyCodeFromString("KEYCODE_" + symbolicName);
        if (code != KeyEvent.KEYCODE_UNKNOWN) {
            return keyCode(code);
        }
        return null;
    }

    /**
     * There is no undo.
     */
    @Override
    public Op imeAction(int editorAction) {
        return getEditorActionOp(editorAction);
    }

    /**
     * There is no undo, because the undo-stack does not survive the jump to another field.
     */
    @Override
    public Op imeActionPrevious() {
        return getEditorActionOp(EditorInfo.IME_ACTION_PREVIOUS);
    }

    /**
     * There is no undo, because the undo-stack does not survive the jump to another field.
     */
    @Override
    public Op imeActionNext() {
        return getEditorActionOp(EditorInfo.IME_ACTION_NEXT);
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

    @Override
    public Deque<Op> getOpStack() {
        return mOpStack;
    }

    @Override
    public Deque<Op> getUndoStack() {
        return mUndoStack;
    }

    @Override
    public ExtractedText getExtractedText() {
        return mInputConnection.getExtractedText(new ExtractedTextRequest(), 0);
    }

    private void pushOp(Op op) {
        mOpStack.push(op);
        Log.i("undo: push op: " + mOpStack.toString());
    }

    private void pushOpUndo(Op op) {
        mUndoStack.push(op);
        Log.i("undo: push undo: " + mUndoStack.toString());
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

    private Op getContextMenuActionOp(final int contextMenuAction) {
        return new Op("contextMenuAction " + contextMenuAction) {
            @Override
            public Op run() {
                if (mInputConnection.performContextMenuAction(contextMenuAction)) {
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
            deleteSurrounding(deletableLength, 0);
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
     * TODO: review
     * we should be able to review the last N ops and undo then if they can be interpreted as
     * a combined op.
     */
    private Op getCommitWithOverwriteOp(final String text) {
        return new Op("add " + text) {
            @Override
            public Op run() {
                // Calculate the length of the text that has changed
                String commonPrefix = greatestCommonPrefix(mPrevText, text);
                int commonPrefixLength = commonPrefix.length();

                mInputConnection.beginBatchEdit();
                final ExtractedText et = getExtractedText();
                final String selectedText = getSelectedText();
                // Delete the part that changed compared to the partial text added earlier.
                int deletableLength = mPrevText.length() - commonPrefixLength;
                if (deletableLength > 0) {
                    deleteSurrounding(deletableLength, 0);
                }

                // Finish if there is nothing to add
                if (text.isEmpty() || commonPrefixLength == text.length()) {
                    mAddedLength -= deletableLength;
                } else {
                    CharSequence leftContext = "";
                    String glue = "";
                    String text1 = text;
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
                        text1 = text.substring(commonPrefixLength);
                        leftContext = commonPrefix;
                        mAddedLength = mAddedLength - deletableLength + text1.length();
                    }
                    text1 = capitalizeIfNeeded(text1, leftContext);
                    mInputConnection.commitText(glue + text1, 1);
                }
                mInputConnection.endBatchEdit();
                return new Op("delete " + mAddedLength) {
                    @Override
                    public Op run() {
                        mInputConnection.beginBatchEdit();
                        boolean success = deleteSurrounding(mAddedLength, 0);
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
                };
            }
        };
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
                // TODO: use getSelection
                final ExtractedText et = getExtractedText();
                if (mInputConnection.commitText(newText, 1)) {
                    undo = new Op("deleteSurroundingText+commitText") {
                        @Override
                        public Op run() {
                            mInputConnection.beginBatchEdit();
                            boolean success = deleteSurrounding(newText.length(), 0);
                            if (success && oldText != null) {
                                success = mInputConnection.commitText(oldText, 1);
                            }
                            if (et != null && success) {
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
     * Go to the Nth match and return the indices of the 1st group in the match if available.
     * If not then return the indices of the whole match.
     * If the match could not be made N times the return the last match (e.g. if n == 0).
     * If no match was found then return {@code null}.
     * TODO: if possible, support negative N to match from end to beginning.
     */
    private Pair<Integer, Integer> matchNth(Pattern pattern, CharSequence input, int n) {
        Matcher matcher = pattern.matcher(input);
        Pair<Integer, Integer> pos = null;
        int end = 0;
        int counter = 0;
        while (matcher.find(end)) {
            counter++;
            int group = 0;
            if (matcher.groupCount() > 0) {
                group = 1;
            }
            pos = new Pair<>(matcher.start(group), matcher.end(group));
            if (counter == n) {
                break;
            }
            int newEnd = matcher.end(group);
            // We require the end position to increase to avoid infinite loop when matching ^.
            if (newEnd <= end) {
                end++;
                if (end >= input.length()) {
                    break;
                }
            } else {
                end = newEnd;
            }
        }
        return pos;
    }

    private Pair<Integer, Integer> matchAtPos(Pattern pattern, CharSequence input, int posStart, int posEnd) {
        Matcher matcher = pattern.matcher(input);
        Pair<Integer, Integer> pos = null;
        while (matcher.find()) {
            int group = 0;
            if (matcher.groupCount() > 0) {
                group = 1;
            }
            if (matcher.start(group) <= posStart) {
                if (posEnd <= matcher.end(group)) {
                    return new Pair<>(matcher.start(group), matcher.end(group));
                }
            } else {
                // Stop searching if the match start only after the cursor.
                return null;
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

    @Override
    public Op moveRel(final int numOfChars) {
        return move(numOfChars, 2);
    }


    @Override
    public Op moveRelSel(final int numOfChars, final int type) {
        return move(numOfChars, type);
    }

    private Op move(final int numberOfChars, final int type) {
        return new Op("moveRel") {
            @Override
            public Op run() {
                Op undo = null;
                mInputConnection.beginBatchEdit();
                ExtractedText extractedText = getExtractedText();
                if (extractedText != null) {
                    int newStart = extractedText.selectionStart;
                    int newEnd = extractedText.selectionEnd;
                    if (type == 0) {
                        newStart += numberOfChars;
                    } else if (type == 1) {
                        newEnd += numberOfChars;
                    } else {
                        if (numberOfChars < 0) {
                            newStart += numberOfChars;
                            newEnd = newStart;
                        } else {
                            newEnd += numberOfChars;
                            newStart = newEnd;
                        }
                    }
                    undo = getOpSetSelection(newStart, newEnd, extractedText.selectionStart, extractedText.selectionEnd).run();
                }
                mInputConnection.endBatchEdit();
                return undo;
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
            for (UtteranceRewriter ur : mRewriters) {
                if (ur == null) {
                    continue;
                }
                UtteranceRewriter.Rewrite rewrite = ur.getRewrite(possibleCommand);
                if (rewrite.isCommand()) {
                    Log.i("applyCommand: isCommand: " + possibleCommand);
                    undo(i).run();
                    return rewrite;
                }
            }
        }
        // TODO: review this
        String newText = text;
        UtteranceRewriter.Rewrite rewrite = null;
        for (UtteranceRewriter ur : mRewriters) {
            if (ur == null) {
                continue;
            }
            rewrite = ur.getRewrite(newText);
            if (rewrite.isCommand()) {
                return rewrite;
            }
            newText = rewrite.mStr;
        }
        if (rewrite == null) {
            rewrite = new UtteranceRewriter.Rewrite(newText);
        }
        return rewrite;
    }

    private boolean deleteSurrounding(int beforeLength, int afterLength) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return mInputConnection.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
        }
        return mInputConnection.deleteSurroundingText(beforeLength, afterLength);
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
