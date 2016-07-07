package ee.ioc.phon.android.speechutils.editor;

import java.util.Deque;

public interface CommandEditor {

    // Commit text

    CommandEditorResult commitFinalResult(String str);

    boolean commitPartialResult(String str);

    // Commands

    boolean goUp();

    boolean goDown();

    boolean goLeft();

    boolean goRight();

    boolean undo(int steps);

    // Apply the last command N times
    boolean apply(int steps);

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

    // Add the key with the given code
    boolean keyCode(int code);

    // Add the key with the given symbolic name
    boolean keyCodeStr(String codeAsStr);

    boolean select(String str);

    boolean selectReBefore(String regex);

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

    boolean imeActionDone();

    boolean imeActionGo();

    boolean imeActionSearch();

    boolean imeActionSend();


    // Other

    CharSequence getText();

    void setUtteranceRewriter(UtteranceRewriter ur);

    Deque<InputConnectionCommandEditor.Op> getUndoStack();

    void reset();
}