package ee.ioc.phon.android.speechutils.editor;

/**
 * TODO: work in progress
 */
public interface CommandEditor {

    // Moving around

    // Go to the previous field
    boolean goToPreviousField();

    // Go to the next field
    boolean goToNextField();

    // Go to the character at the given position
    boolean goToCharacterPosition(int pos);

    // Selecting
    boolean selectAll();


    // Copy, paste

    // Copy the 1st arg to the clipboard
    boolean copy(String str);

    // Paste the content of the clipboard
    boolean paste();
}
