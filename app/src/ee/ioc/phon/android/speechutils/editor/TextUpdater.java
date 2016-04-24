package ee.ioc.phon.android.speechutils.editor;

import android.view.inputmethod.InputConnection;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUpdater {

    // Maximum number of characters that left-swipe is willing to delete
    private static final int MAX_DELETABLE_CONTEXT = 100;
    // Token optionally preceded by whitespace
    private static final Pattern WHITESPACE_AND_TOKEN = Pattern.compile("\\s*\\w+");

    private String mPrevText = "";

    private InputConnection mInputConnection;

    public TextUpdater() {
    }

    public void setInputConnection(InputConnection inputConnection) {
        mInputConnection = inputConnection;
    }

    /**
     * Writes the text into the text field and forgets the previous entry.
     */
    public void commitFinalResult(String text) {
        commitText(text);
        mPrevText = "";
    }

    /**
     * Writes the text into the text field and stores it for future reference.
     */
    public void commitPartialResult(String text) {
        commitText(text);
        mPrevText = text;
    }

    public void addNewline() {
        mInputConnection.commitText("\n", 1);
    }

    public void addSpace() {
        mInputConnection.commitText(" ", 1);
    }

    /**
     * Deletes all characters up to the leftmost whitespace from the cursor (including the whitespace).
     * If something is selected then delete the selection.
     * TODO: maybe expensive?
     */
    public void deleteWord() {
        // If something is selected then delete the selection and return
        if (mInputConnection.getSelectedText(0) != null) {
            mInputConnection.commitText("", 0);
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
                    mInputConnection.deleteSurroundingText(beforeCursorLength - lastIndex, 0);
                } else if (beforeCursorLength < MAX_DELETABLE_CONTEXT) {
                    mInputConnection.deleteSurroundingText(beforeCursorLength, 0);
                }
            }
        }
    }


    /**
     * Updates the text field, modifying only the parts that have changed.
     */
    private void commitText(String text) {
        if (text != null && text.length() > 0) {
            // Calculate the length of the text that has changed
            String commonPrefix = greatestCommonPrefix(mPrevText, text);
            int commonPrefixLength = commonPrefix.length();
            int prevLength = mPrevText.length();
            int deletableLength = prevLength - commonPrefixLength;

            if (deletableLength > 0) {
                mInputConnection.deleteSurroundingText(deletableLength, 0);
            }

            if (commonPrefixLength == text.length()) {
                return;
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
                char firstChar = text.charAt(0);

                if (!(leftContext.length() == 0
                        || Constants.CHARACTERS_WS.contains(firstChar)
                        || Constants.CHARACTERS_PUNCT.contains(firstChar)
                        || Constants.CHARACTERS_WS.contains(leftContext.charAt(leftContext.length() - 1)))) {
                    glue = " ";
                }
            } else {
                text = text.substring(commonPrefixLength);
            }

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
                    text = newText;
                }
            }

            mInputConnection.commitText(glue + text, 1);
        }
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