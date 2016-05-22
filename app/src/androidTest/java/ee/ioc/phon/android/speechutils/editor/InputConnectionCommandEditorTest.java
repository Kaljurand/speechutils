package ee.ioc.phon.android.speechutils.editor;

import android.content.Context;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class InputConnectionCommandEditorTest {

    InputConnectionCommandEditor mEditor;

    @Before
    public void before() {
        Context context = getInstrumentation().getContext();
        EditText view = new EditText(context);
        //view.setText("elas metsas mutionu, keset kuuski noori-vanu");
        EditorInfo editorInfo = new EditorInfo();
        //editorInfo.initialSelStart = 12;
        //editorInfo.initialSelEnd = 19;
        InputConnection connection = view.onCreateInputConnection(editorInfo);
        //InputConnection connection = new BaseInputConnection(view, true);
        mEditor = new InputConnectionCommandEditor();
        mEditor.setInputConnection(connection);
    }

    @Test
    public void test01() {
        assertNotNull(mEditor.getInputConnection());
    }

    @Test
    public void test02() {
        boolean success = mEditor.commitFinalResult("start12345 67890");
        assertThat(success, is(true));
        InputConnection ic = mEditor.getInputConnection();
        CharSequence text = ic.getTextBeforeCursor(5, 0);
        assertThat(text.toString(), is(new String("67890")));

        success = mEditor.deleteLeftWord();
        assertThat(success, is(true));

        text = ic.getTextBeforeCursor(5, 0);
        assertThat(text.toString(), is(new String("12345")));

        success = mEditor.delete("12345");
        assertThat(success, is(true));

        text = ic.getTextBeforeCursor(5, 0);
        assertThat(text.toString(), is(new String("Start")));
    }


    // TODO: @Test
    // Can't create handler inside thread that has not called Looper.prepare()
    public void test03() {
        assertThat(mEditor.copy(), is(true));
        assertThat(mEditor.paste(), is(true));
        assertThat(mEditor.paste(), is(true));
    }

    @Test
    public void test04() {
        assertThat(mEditor.commitPartialResult("...123"), is(true));
        assertThat(mEditor.commitPartialResult("...124"), is(true));
        assertThat(mEditor.commitFinalResult("...1245"), is(true));
        assertThat(mEditor.goToCharacterPosition(4), is(true));
        String text = mEditor.getInputConnection().getTextBeforeCursor(10, 0).toString();
        assertThat(text, is(new String("...1")));
    }

    @Test
    public void test06() {
        boolean success = mEditor.go();
        assertThat(success, is(true));
    }
}