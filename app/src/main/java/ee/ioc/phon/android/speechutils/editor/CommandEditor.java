package ee.ioc.phon.android.speechutils.editor;

import android.view.inputmethod.ExtractedText;

import java.util.Collection;
import java.util.Deque;

/**
 * Most methods return an operation (op), which when run returns an undo operation.
 */
public interface CommandEditor {

    // Commit text

    CommandEditorResult commitFinalResult(String text);

    boolean commitPartialResult(String text);

    boolean runOp(Op op);

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

    // Moving around in the string

    // Go to the character at the given position.
    // Negative positions start counting from the end of the text, e.g.
    // -1 == end of text
    Op goToCharacterPosition(int pos);

    // Move the cursor forward by the given number of characters
    Op goForward(int numOfChars);

    // Move the cursor backward by the given number of characters
    Op goBackward(int numOfChars);

    // Add the key with the given code
    Op keyCode(int code);

    // Add the key with the given symbolic name
    Op keyCodeStr(String codeAsStr);

    // Selection commands

    Op select(String str);

    Op selectReBefore(String regex);

    Op selectReAfter(String regex, int n);

    Op selectAll();

    // Apply a regular expression to the selection,
    // and replace the matches with the given replacement string.
    Op replaceSelRe(String regex, String repl);

    // Reset selection
    Op resetSel();

    // Context menu actions
    Op cut();

    Op copy();

    Op paste();

    Op cutAll();

    Op copyAll();

    Op deleteAll();

    // Preference actions

    // Save the current selection under the given key into clipboard
    Op saveSel(String key);

    // Load the string saved under the given key from the clipboard,
    // and replace the current selection with the string.
    Op loadSel(String key);

    // Replace the current selection with the pretty-printed clipboard
    Op showClipboard();

    // Clear the clipboard
    Op clearClipboard();

    // Editing

    Op deleteLeftWord();

    Op delete(String text);

    Op replace(String text1, String text2);

    // Commands applied to the current selection

    // Replace selection
    Op replaceSel(String str);

    // Uppercase selection
    Op ucSel();

    // Lowercase selection
    Op lcSel();

    // Increment selection
    Op incSel();

    // IME actions

    // Go to the previous field
    Op goToPreviousField();

    // Go to the next field
    Op goToNextField();

    Op imeActionDone();

    Op imeActionGo();

    Op imeActionSearch();

    Op imeActionSend();

    // Other

    ExtractedText getExtractedText();

    CharSequence getText();

    void setUtteranceRewriter(UtteranceRewriter ur);

    Deque<Op> getOpStack();

    Deque<Op> getUndoStack();

    void reset();

    Op combineOps(Collection<Op> ops);

    Op getOpFromText(String text);
}