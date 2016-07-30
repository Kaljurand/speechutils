package ee.ioc.phon.android.speechutils.editor;

import java.util.Deque;

/**
 * Most methods return an operation (op), which when run returns an undo operation.
 */
public interface CommandEditor {

    // Commit text

    CommandEditorResult commitFinalResult(String str);

    boolean commitPartialResult(String str);

    // Commands

    Op goUp();

    Op goDown();

    Op goLeft();

    Op goRight();

    Op undo(int steps);

    // Combine the last N commands
    Op combine(int steps);

    // Apply the last command N times
    Op apply(int steps);

    // Moving between fields

    // Go to the previous field
    Op goToPreviousField();

    // Go to the next field
    Op goToNextField();

    // Moving around in the string

    // Go to the character at the given position
    Op goToCharacterPosition(int pos);

    // Move the cursor forward by the given number of characters
    Op goForward(int numOfChars);

    // Move the cursor backward by the given number of characters
    Op goBackward(int numOfChars);

    // Go to the end of the text
    Op goToEnd();

    // Add the key with the given code
    Op keyCode(int code);

    // Add the key with the given symbolic name
    Op keyCodeStr(String codeAsStr);

    Op select(String str);

    Op selectReBefore(String regex);

    // Reset selection
    Op resetSel();

    Op selectAll();

    // Context menu actions
    Op cut();

    Op copy();

    Op paste();

    Op cutAll();

    Op copyAll();

    Op deleteAll();

    // Editing

    Op deleteLeftWord();

    Op delete(String str);

    Op replace(String str1, String str2);

    // Replace selection
    Op replaceSel(String str1);

    // Uppercase selection
    Op ucSel();

    // Lowercase selection
    Op lcSel();

    // Increment selection
    Op incSel();

    Op imeActionDone();

    Op imeActionGo();

    Op imeActionSearch();

    Op imeActionSend();


    // Other

    CharSequence getText();

    void setUtteranceRewriter(UtteranceRewriter ur);

    Deque<Op> getOpStack();

    Deque<Op> getUndoStack();

    void pushOp(Op op);

    void popOp();

    void pushOpUndo(Op op);

    void reset();
}