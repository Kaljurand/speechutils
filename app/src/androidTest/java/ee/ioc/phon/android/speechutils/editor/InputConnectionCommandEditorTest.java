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

    private InputConnectionCommandEditor mEditor;

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
        assertThat(mEditor.commitFinalResult("start12345 67890"), is(true));
        assertThat(getTextBeforeCursor(5), is("67890"));
        assertThat(mEditor.deleteLeftWord(), is(true));
        assertThat(getTextBeforeCursor(5), is("12345"));
        assertThat(mEditor.delete("12345"), is(true));
        assertThat(getTextBeforeCursor(5), is("Start"));
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
        assertThat(getTextBeforeCursor(10), is("...1"));
    }

    @Test
    public void test05() {
        assertThat(mEditor.commitFinalResult("a12345 67890_12345"), is(true));
        assertThat(mEditor.select("12345"), is(true));
        assertThat(getTextBeforeCursor(2), is("0_"));
        assertThat(mEditor.deleteLeftWord(), is(true));
        assertThat(getTextBeforeCursor(2), is("0_"));
        assertThat(mEditor.deleteLeftWord(), is(true));
        assertThat(getTextBeforeCursor(2), is("45"));
    }

    @Test
    public void test06() {
        assertThat(mEditor.commitFinalResult("a12345 67890_12345"), is(true));
        assertThat(mEditor.replace("12345", "abcdef"), is(true));
        assertThat(mEditor.addSpace(), is(true));
        assertThat(mEditor.replace("12345", "ABC"), is(true));
        assertThat(getTextBeforeCursor(2), is("BC"));
        assertThat(mEditor.addNewline(), is(true));
        assertThat(mEditor.addSpace(), is(true));
        assertThat(mEditor.goToCharacterPosition(9), is(true));
        assertThat(getTextBeforeCursor(2), is("67"));
    }

    private String getTextBeforeCursor(int n) {
        return mEditor.getInputConnection().getTextBeforeCursor(n, 0).toString();
    }
}