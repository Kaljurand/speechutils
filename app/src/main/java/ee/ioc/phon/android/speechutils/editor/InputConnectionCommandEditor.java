package ee.ioc.phon.android.speechutils.editor;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO: keep track of added spaces
 */
public class InputConnectionCommandEditor implements CommandEditor {

    // Maximum number of utterances that a command can contain + 1
    private static final int MAX_UTT_IN_COMMAND = 4;

    // Maximum number of characters that left-swipe is willing to delete
    private static final int MAX_DELETABLE_CONTEXT = 100;
    // Token optionally preceded by whitespace
    private static final Pattern WHITESPACE_AND_TOKEN = Pattern.compile("\\s*\\w+");

    // Reference to the current selection {}, {uc}, {inc}, ...
    // TODO: uc and inc and commands, but currently not supported
    private static final Pattern SELREF = Pattern.compile("\\{([a-z]*)\\}");
    private static final Pattern ALL = Pattern.compile("^(.*)$");

    private String mPrevText = "";

    private List<String> mFinalStrings = new ArrayList<>();

    private int mGlueCount = 0;

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

    /**
     * Writes the text into the text field and forgets the previous entry.
     */
    @Override
    public UtteranceRewriter.Triple commitFinalResult(String text) {
        UtteranceRewriter.Triple triple = null;
        if (mUtteranceRewriter == null) {
            commitText(text, true);
        } else {
            triple = mUtteranceRewriter.rewrite(text);
            String textRewritten = triple.mStr;
            boolean isCommand = mCommandEditorManager.execute(triple.mId, triple.mArgs, textRewritten);
            mFinalStrings.add(textRewritten);

            // TODO: if the command failed then try to make a command by concatenating
            // the previous utterances
            if (!isCommand) {
                int len = mFinalStrings.size();
                for (int i = 2; i < Math.min(MAX_UTT_IN_COMMAND, len); i++) {
                    String possibleCommand = TextUtils.join(" ", mFinalStrings.subList(len - i, len));
                    triple = mUtteranceRewriter.rewrite(possibleCommand);
                    textRewritten = triple.mStr;
                    if (triple.mId != null) {
                        isCommand = mCommandEditorManager.execute(triple.mId, triple.mArgs);
                    }
                }
            }
            if (isCommand) {
                mFinalStrings.clear();
            }
        }
        mPrevText = "";
        mGlueCount = 0;
        return triple;
    }

    /**
     * Writes the text into the text field and stores it for future reference.
     */
    @Override
    public boolean commitPartialResult(String text) {
        String textRewritten = rewrite(text);
        boolean success = commitText(textRewritten, false);
        if (success) {
            mPrevText = textRewritten;
        }
        return success;
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
        return mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
    }

    @Override
    public boolean goDown() {
        return mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
    }

    @Override
    public boolean goLeft() {
        return mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
    }

    @Override
    public boolean goRight() {
        return mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
    }

    // TODO: do not use mFinalStrings but a proper undo stack
    @Override
    public boolean undo() {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        int idx = mFinalStrings.size() - 1;
        if (idx >= 0) {
            success = mInputConnection.deleteSurroundingText(mFinalStrings.remove(idx).length(), 0);
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean goToPreviousField() {
        return mInputConnection.performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
    }

    @Override
    public boolean goToNextField() {
        return mInputConnection.performEditorAction(EditorInfo.IME_ACTION_NEXT);
    }

    @Override
    public boolean goToCharacterPosition(int pos) {
        return mInputConnection.setSelection(pos, pos);
    }

    @Override
    public boolean goForward(int numberOfChars) {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        ExtractedText extractedText = mInputConnection.getExtractedText(new ExtractedTextRequest(), 0);
        if (extractedText != null) {
            success = goToCharacterPosition(extractedText.selectionEnd + numberOfChars);
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean goBackward(int numberOfChars) {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        ExtractedText extractedText = mInputConnection.getExtractedText(new ExtractedTextRequest(), 0);
        if (extractedText != null) {
            success = goToCharacterPosition(extractedText.selectionStart - numberOfChars);
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean goToEnd() {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        ExtractedText extractedText = mInputConnection.getExtractedText(new ExtractedTextRequest(), 0);
        if (extractedText != null && extractedText.text != null) {
            success = goToCharacterPosition(extractedText.text.length());
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
        CharSequence text = getText();
        if (text != null) {
            success = mInputConnection.setSelection(0, text.length());
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean cut() {
        return mInputConnection.performContextMenuAction(android.R.id.cut);
    }

    @Override
    public boolean cutAll() {
        mInputConnection.beginBatchEdit();
        boolean success = selectAll() && cut();
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean deleteAll() {
        mInputConnection.beginBatchEdit();
        boolean success = selectAll() && mInputConnection.commitText("", 0);
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean copy() {
        return mInputConnection.performContextMenuAction(android.R.id.copy);
    }

    @Override
    public boolean copyAll() {
        mInputConnection.beginBatchEdit();
        boolean success = selectAll() && copy();
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean paste() {
        return mInputConnection.performContextMenuAction(android.R.id.paste);
    }

    @Override
    public boolean addSpace() {
        mInputConnection.commitText(" ", 1);
        return true;
    }

    @Override
    public boolean addNewline() {
        mInputConnection.commitText("\n", 1);
        return true;
    }

    @Override
    public boolean resetSel() {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        ExtractedText et = getExtractedText();
        if (et != null) {
            success = mInputConnection.setSelection(et.selectionEnd, et.selectionEnd);
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    /**
     * Deletes all characters up to the leftmost whitespace from the cursor (including the whitespace).
     * If something is selected then delete the selection.
     * TODO: maybe expensive?
     */
    @Override
    public boolean deleteLeftWord() {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        // If something is selected then delete the selection and return
        if (mInputConnection.getSelectedText(0) != null) {
            success = mInputConnection.commitText("", 0);
        } else {
            CharSequence beforeCursor = mInputConnection.getTextBeforeCursor(MAX_DELETABLE_CONTEXT, 0);
            if (beforeCursor != null) {
                int beforeCursorLength = beforeCursor.length();
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
            }
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean select(String str) {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        int index = lastIndexOf(str);
        if (index > -1) {
            success = mInputConnection.setSelection(index, index + str.length());
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean delete(String str) {
        return replace(str, "");
    }

    @Override
    public boolean replace(String str1, String str2) {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        int index = lastIndexOf(str1);
        if (index >= 0) {
            success = mInputConnection.setSelection(index, index);
            if (success) {
                success = mInputConnection.deleteSurroundingText(0, str1.length());
                if (!str2.isEmpty()) {
                    success = mInputConnection.commitText(str2, 0);
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
        String str2 = ALL.matcher(getSelectedText()).replaceAll(out);
        success = mInputConnection.commitText(str2, 0);
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean ucSel() {
        boolean success;
        mInputConnection.beginBatchEdit();
        success = mInputConnection.commitText(getSelectedText().toUpperCase(), 0);
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean lcSel() {
        boolean success;
        mInputConnection.beginBatchEdit();
        success = mInputConnection.commitText(getSelectedText().toLowerCase(), 0);
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean incSel() {
        boolean success = false;
        mInputConnection.beginBatchEdit();
        try {
            int number = Integer.parseInt(getSelectedText());
            success = mInputConnection.commitText(String.valueOf(number + 1), 0);
        } catch (NumberFormatException e) {
            // Intentional
        }
        mInputConnection.endBatchEdit();
        return success;
    }

    @Override
    public boolean go() {
        // Does not work on Google Searchbar
        // mInputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE);

        // Works in Google Searchbar, GF Translator, but NOT in the Firefox search widget
        //mInputConnection.performEditorAction(EditorInfo.IME_ACTION_GO);

        return mInputConnection.performEditorAction(EditorInfo.IME_ACTION_SEARCH);
    }

    /**
     * Updates the text field, modifying only the parts that have changed.
     */
    @Override
    public boolean commitText(String text, boolean overwriteSelection) {
        mInputConnection.beginBatchEdit();
        if (!overwriteSelection) {
            CharSequence cs = mInputConnection.getSelectedText(0);
            if (cs != null && cs.length() > 0) {
                mInputConnection.endBatchEdit();
                return false;
            }
        }
        // Calculate the length of the text that has changed
        String commonPrefix = greatestCommonPrefix(mPrevText, text);
        int commonPrefixLength = commonPrefix.length();
        int prevLength = mPrevText.length();
        int deletableLength = prevLength - commonPrefixLength;

        // Delete the glue symbol if present
        if (text.isEmpty()) {
            deletableLength += mGlueCount;
        }

        if (deletableLength > 0) {
            mInputConnection.deleteSurroundingText(deletableLength, 0);
        }

        if (text.isEmpty() || commonPrefixLength == text.length()) {
            mInputConnection.endBatchEdit();
            return true;
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

        if (" ".equals(glue)) {
            mGlueCount = 1;
        }

        text = capitalizeIfNeeded(text, leftContext);
        mInputConnection.commitText(glue + text, 1);
        mInputConnection.endBatchEdit();
        return true;
    }

    private String rewrite(String str) {
        if (mUtteranceRewriter == null) {
            return str;
        }
        UtteranceRewriter.Triple triple = mUtteranceRewriter.rewrite(str);
        return triple.mStr;
    }

    /**
     * Using case-insensitive matching.
     * TODO: this might not work with some Unicode characters
     *
     * @param str search string
     * @return index of the last occurrence of the given string
     */
    private int lastIndexOf(String str) {
        ExtractedText extractedText = getExtractedText();
        if (extractedText == null) {
            return -1;
        }
        int start = extractedText.selectionStart;
        //int end = extractedText.selectionEnd;
        CharSequence allText = extractedText.text;
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
