package ee.ioc.phon.android.speechutils.editor;

import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class InputConnectionCommandEditor implements CommandEditor {

    private InputConnection mInputConnection;

    public InputConnectionCommandEditor() {
    }

    public void setInputConnection(InputConnection inputConnection) {
        mInputConnection = inputConnection;
    }

    @Override
    public boolean goToPreviousField() {
        mInputConnection.performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
        return false;
    }

    @Override
    public boolean goToNextField() {
        mInputConnection.performEditorAction(EditorInfo.IME_ACTION_NEXT);
        return false;
    }

    @Override
    public boolean goToCharacterPosition(int pos) {
        mInputConnection.setSelection(pos, pos);
        return false;
    }

    @Override
    public boolean selectAll() {
        mInputConnection.performContextMenuAction(android.R.id.selectAll);
        return true;
    }

    @Override
    public boolean copy(String str) {
        return false;
    }

    @Override
    public boolean paste() {
        return false;
    }
}