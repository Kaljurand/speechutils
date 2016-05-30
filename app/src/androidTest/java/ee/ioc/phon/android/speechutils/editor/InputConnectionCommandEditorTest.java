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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class InputConnectionCommandEditorTest {

    private static final List<Command> COMMANDS;

    static {
        List<Command> list = new ArrayList<>();
        list.add(new Command("old_word", "new_word"));
        list.add(new Command("r2_old", "r2_new"));
        list.add(new Command("s/(.*)/(.*)/", "", "replace", new String[]{"$1", "$2"}));
        list.add(new Command("connect (.*) and (.*)", "", "replace", new String[]{"$1 $2", "$1-$2"}));
        list.add(new Command("delete (.*)", "", "replace", new String[]{"$1", ""}));
        list.add(new Command("underscore (.*)", "", "replace", new String[]{"$1", "_$1_"}));
        COMMANDS = Collections.unmodifiableList(list);
    }

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
        mEditor.setUtteranceRewriter(new UtteranceRewriter(COMMANDS));
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

    @Test
    public void test07() {
        assertThat(mEditor.commitFinalResult("123456789"), is(true));
        assertThat(mEditor.goToCharacterPosition(2), is(true));
        assertThat(getTextBeforeCursor(2), is("12"));
        assertThat(mEditor.goToEnd(), is(true));
        assertThat(getTextBeforeCursor(2), is("89"));
    }

    @Test
    public void test08() {
        assertThat(mEditor.commitFinalResult("old_word"), is(true));
        assertThat(getTextBeforeCursor(8), is("New_word"));
    }

    @Test
    public void test09() {
        assertThat(mEditor.commitFinalResult("r2_old"), is(true));
        assertThat(getTextBeforeCursor(8), is("R2_new"));
    }

    @Test
    public void test10() {
        assertThat(mEditor.commitFinalResult("test old_word test"), is(true));
        assertThat(getTextBeforeCursor(13), is("new_word test"));
    }

    @Test
    public void test11() {
        assertThat(mEditor.commitFinalResult("test old_word"), is(true));
        assertThat(mEditor.commitFinalResult("s/old_word/new_word/"), is(true));
        assertThat(getTextBeforeCursor(8), is("new_word"));
    }

    @Test
    public void test12() {
        assertThat(mEditor.commitFinalResult("test word1 word2"), is(true));
        assertThat(mEditor.commitFinalResult("connect word1 and word2"), is(true));
        assertThat(getTextBeforeCursor(11), is("word1-word2"));
    }

    @Test
    public void test13() {
        assertThat(mEditor.commitFinalResult("test word1 word2"), is(true));
        assertThat(mEditor.commitFinalResult("connect word1"), is(true));
        assertThat(mEditor.commitFinalResult("and"), is(true));
        assertThat(mEditor.commitFinalResult("word2"), is(true));
        assertThat(getTextBeforeCursor(11), is("word1-word2"));
    }

    @Test
    public void test14() {
        assertThat(mEditor.commitFinalResult("test word1"), is(true));
        assertThat(mEditor.addSpace(), is(true));
        assertThat(mEditor.commitFinalResult("word2"), is(true));
        assertThat(getTextBeforeCursor(11), is("word1 word2"));
        assertThat(mEditor.commitFinalResult("connect word1 and word2"), is(true));
        assertThat(getTextBeforeCursor(11), is("word1-word2"));
    }

    @Test
    public void test15() {
        assertThat(mEditor.commitFinalResult("test word1"), is(true));
        assertThat(mEditor.addSpace(), is(true));
        assertThat(mEditor.commitFinalResult("word2"), is(true));
        assertThat(getTextBeforeCursor(11), is("word1 word2"));
        assertThat(mEditor.deleteAll(), is(true));
        assertThat(getTextBeforeCursor(1), is(""));
    }

    /**
     * If command does not fully match then its replacement is ignored.
     */
    @Test
    public void test16() {
        assertThat(mEditor.commitFinalResult("I will delete something"), is(true));
        assertThat(getTextBeforeCursor(9), is("something"));
    }

    /**
     * TODO: incorrectly replaces with "_some_" instead of "_SOME_"
     */
    @Test
    public void test17() {
        assertThat(mEditor.commitFinalResult("this is SOME word"), is(true));
        assertThat(mEditor.commitFinalResult("underscore some"), is(true));
        assertThat(getTextBeforeCursor(6), is("_SOME_"));
    }

    @Test
    public void test18() {
        assertThat(mEditor.commitFinalResult("test word1"), is(true));
        assertThat(mEditor.addSpace(), is(true));
        assertThat(mEditor.commitFinalResult("word2"), is(true));
        assertThat(getTextBeforeCursor(11), is("word1 word2"));
        assertThat(mEditor.cutAll(), is(true));
        assertThat(getTextBeforeCursor(1), is(""));
        assertThat(mEditor.paste(), is(true));
        assertThat(getTextBeforeCursor(11), is("word1 word2"));
    }

    private String getTextBeforeCursor(int n) {
        return mEditor.getInputConnection().getTextBeforeCursor(n, 0).toString();
    }
}