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

    boolean select(String str);

    // Reset selection
    boolean reset();

    // Context menu actions
    boolean selectAll();

    boolean cut();

    boolean copy();

    boolean paste();

    // Editing

    boolean capitalize(String str);

    boolean addSpace();

    boolean addNewline();

    boolean deleteLeftWord();

    boolean delete(String str);

    boolean replace(String str1, String str2);

    /**
     * Performs the Search-action, e.g. to launch search on a searchbar.
     */
    boolean go();
}