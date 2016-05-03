package ee.ioc.phon.android.speechutils.editor;

/**
 * TODO: work in progress
 */
public interface CommandEditor {

    boolean commitFinalResult(String str);

    boolean commitPartialResult(String str);

    // Moving between fields

    // Go to the previous field
    boolean goToPreviousField();

    // Go to the next field
    boolean goToNextField();

    // Moving around in the string

    // Go to the character at the given position
    boolean goToCharacterPosition(int pos);

    // Selecting
    boolean selectAll();

    // Reset selection
    boolean reset();

    // Copy, paste

    // Copy the 1st arg to the clipboard
    boolean copy(String str);

    // Paste the content of the clipboard
    boolean paste();

    // Editing

    boolean capitalize(String str);

    boolean addSpace();

    boolean addNewline();

    boolean deleteLeftWord();

    boolean replace(String str1, String str2);

    /**
     * Performs the Search-action, e.g. to launch search on a searchbar.
     */
    boolean go();
}