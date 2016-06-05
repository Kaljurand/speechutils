package ee.ioc.phon.android.speechutils.editor;

/**
 * TODO: work in progress
 */
public interface CommandEditor {

    CharSequence getText();

    void setUtteranceRewriter(UtteranceRewriter ur);

    // Adds text at the cursor, possibly overwriting a selection.
    // Returns true if text was added.
    boolean commitText(String str, boolean overwriteSelection);

    UtteranceRewriter.Triple commitFinalResult(String str);

    boolean commitPartialResult(String str);

    // Moving between fields

    // Go to the previous field
    boolean goToPreviousField();

    // Go to the next field
    boolean goToNextField();

    // Moving around in the string

    // Go to the character at the given position
    boolean goToCharacterPosition(int pos);

    // Move the cursor forward by the given number of characters
    boolean goForward(int numOfChars);

    // Move the cursor backward by the given number of characters
    boolean goBackward(int numOfChars);

    // Go to the end of the text
    boolean goToEnd();

    boolean select(String str);

    // Reset selection
    boolean resetSel();

    boolean selectAll();

    // Context menu actions
    boolean cut();

    boolean copy();

    boolean paste();

    boolean cutAll();

    boolean copyAll();

    boolean deleteAll();

    // Editing

    boolean addSpace();

    boolean addNewline();

    boolean deleteLeftWord();

    boolean delete(String str);

    boolean replace(String str1, String str2);

    // Replace selection
    boolean replaceSel(String str1);

    // Uppercase selection
    boolean ucSel();

    // Lowercase selection
    boolean lcSel();

    // Increment selection
    boolean incSel();

    /**
     * Performs the Search-action, e.g. to launch search on a searchbar.
     */
    boolean go();
}