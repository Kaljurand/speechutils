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
import static org.junit.Assert.assertTrue;

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
        // replaceSel(x)
        list.add(new Command("select (.*)", "", "select", new String[]{"$1"}));
        list.add(new Command("selection_replace (.*)", "", "replaceSel", new String[]{"$1"}));
        list.add(new Command("selection_underscore", "", "replaceSel", new String[]{"_{}_"}));
        list.add(new Command("selection_double", "", "replaceSel", new String[]{"{}{}"}));
        list.add(new Command("selection_uppercase", "", "replaceSel", new String[]{"\"{uc}\""}));
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
        assertTrue(mEditor.commitFinalResult("start12345 67890"));
        assertThat(getTextBeforeCursor(5), is("67890"));
        assertTrue(mEditor.deleteLeftWord());
        assertThat(getTextBeforeCursor(5), is("12345"));
        assertTrue(mEditor.delete("12345"));
        assertThat(getTextBeforeCursor(5), is("Start"));
    }


    // TODO: @Test
    // Can't create handler inside thread that has not called Looper.prepare()
    public void test03() {
        assertTrue(mEditor.copy());
        assertTrue(mEditor.paste());
        assertTrue(mEditor.paste());
    }

    @Test
    public void test04() {
        assertTrue(mEditor.commitPartialResult("...123"));
        assertTrue(mEditor.commitPartialResult("...124"));
        assertTrue(mEditor.commitFinalResult("...1245"));
        assertTrue(mEditor.goToCharacterPosition(4));
        assertThat(getTextBeforeCursor(10), is("...1"));
    }

    @Test
    public void test05() {
        assertTrue(mEditor.commitFinalResult("a12345 67890_12345"));
        assertTrue(mEditor.select("12345"));
        assertThat(getTextBeforeCursor(2), is("0_"));
        assertTrue(mEditor.deleteLeftWord());
        assertThat(getTextBeforeCursor(2), is("0_"));
        assertTrue(mEditor.deleteLeftWord());
        assertThat(getTextBeforeCursor(2), is("45"));
    }

    @Test
    public void test06() {
        assertTrue(mEditor.commitFinalResult("a12345 67890_12345"));
        assertTrue(mEditor.replace("12345", "abcdef"));
        assertTrue(mEditor.addSpace());
        assertTrue(mEditor.replace("12345", "ABC"));
        assertThat(getTextBeforeCursor(2), is("BC"));
        assertTrue(mEditor.addNewline());
        assertTrue(mEditor.addSpace());
        assertTrue(mEditor.goToCharacterPosition(9));
        assertThat(getTextBeforeCursor(2), is("67"));
    }

    @Test
    public void test07() {
        assertTrue(mEditor.commitFinalResult("123456789"));
        assertTrue(mEditor.goToCharacterPosition(2));
        assertThat(getTextBeforeCursor(2), is("12"));
        assertTrue(mEditor.goToEnd());
        assertThat(getTextBeforeCursor(2), is("89"));
    }

    @Test
    public void test08() {
        assertTrue(mEditor.commitFinalResult("old_word"));
        assertThat(getTextBeforeCursor(8), is("New_word"));
    }

    @Test
    public void test09() {
        assertTrue(mEditor.commitFinalResult("r2_old"));
        assertThat(getTextBeforeCursor(8), is("R2_new"));
    }

    @Test
    public void test10() {
        assertTrue(mEditor.commitFinalResult("test old_word test"));
        assertThat(getTextBeforeCursor(13), is("new_word test"));
    }

    @Test
    public void test11() {
        assertTrue(mEditor.commitFinalResult("test old_word"));
        assertTrue(mEditor.commitFinalResult("s/old_word/new_word/"));
        assertThat(getTextBeforeCursor(8), is("new_word"));
    }

    @Test
    public void test12() {
        assertTrue(mEditor.commitFinalResult("test word1 word2"));
        assertTrue(mEditor.commitFinalResult("connect word1 and word2"));
        assertThat(getTextBeforeCursor(11), is("word1-word2"));
    }

    @Test
    public void test13() {
        assertTrue(mEditor.commitFinalResult("test word1 word2"));
        assertTrue(mEditor.commitFinalResult("connect word1"));
        assertTrue(mEditor.commitFinalResult("and"));
        assertTrue(mEditor.commitFinalResult("word2"));
        assertThat(getTextBeforeCursor(11), is("word1-word2"));
    }

    @Test
    public void test14() {
        assertTrue(mEditor.commitFinalResult("test word1"));
        assertTrue(mEditor.addSpace());
        assertTrue(mEditor.commitFinalResult("word2"));
        assertThat(getTextBeforeCursor(11), is("word1 word2"));
        assertTrue(mEditor.commitFinalResult("connect word1 and word2"));
        assertThat(getTextBeforeCursor(11), is("word1-word2"));
    }

    @Test
    public void test15() {
        assertTrue(mEditor.commitFinalResult("test word1"));
        assertTrue(mEditor.addSpace());
        assertTrue(mEditor.commitFinalResult("word2"));
        assertThat(getTextBeforeCursor(11), is("word1 word2"));
        assertTrue(mEditor.deleteAll());
        assertThat(getTextBeforeCursor(1), is(""));
    }

    /**
     * If command does not fully match then its replacement is ignored.
     */
    @Test
    public void test16() {
        assertTrue(mEditor.commitFinalResult("I will delete something"));
        assertThat(getTextBeforeCursor(9), is("something"));
    }

    @Test
    public void test17() {
        assertTrue(mEditor.commitFinalResult("there are word1 and word2..."));
        assertTrue(mEditor.commitFinalResult("select word1 and word2"));
        assertTrue(mEditor.goToEnd());
        assertThat(getTextBeforeCursor(8), is("word2..."));
    }

    @Test
    public void test18() {
        assertTrue(mEditor.commitFinalResult("there are word1 and word2..."));
        assertTrue(mEditor.commitFinalResult("select word1 and word2"));
        assertTrue(mEditor.commitFinalResult("selection_replace REPL"));
        assertThatEndsWith("re REPL...");
    }

    @Test
    public void test19() {
        assertTrue(mEditor.commitFinalResult("there are word1 and word2..."));
        assertTrue(mEditor.commitFinalResult("select word1 and word2"));
        assertTrue(mEditor.commitFinalResult("selection_underscore"));
        assertThatEndsWith("are _word1 and word2_...");
    }

    @Test
    public void test20() {
        assertTrue(mEditor.commitFinalResult("a"));
        assertTrue(mEditor.commitFinalResult("select a"));
        assertTrue(mEditor.commitFinalResult("selection_double"));
        // TODO: maybe keep the selection after the command
        assertTrue(mEditor.commitFinalResult("selection_double"));
        assertTrue(mEditor.goToEnd());
        assertThat(getTextBeforeCursor(5), is("AA"));
    }

    @Test
    public void test21() {
        assertTrue(mEditor.commitFinalResult("there are word1 and word2..."));
        assertTrue(mEditor.commitFinalResult("select word1 and word2"));
        assertTrue(mEditor.commitFinalResult("selection_uppercase"));
        assertTrue(mEditor.goToEnd());
        assertThat(getTextBeforeCursor(24), is("are \"WORD1 AND WORD2\"..."));
    }

    /**
     * TODO: incorrectly replaces with "_some_" instead of "_SOME_"
     */
    @Test
    public void test31() {
        assertTrue(mEditor.commitFinalResult("this is SOME word"));
        assertTrue(mEditor.commitFinalResult("underscore some"));
        assertThat(getTextBeforeCursor(6), is("_SOME_"));
    }

    /**
     * Same as before but using selection.
     */
    @Test
    public void test32() {
        assertTrue(mEditor.commitFinalResult("this is SOME word"));
        assertTrue(mEditor.commitFinalResult("select some"));
        assertTrue(mEditor.commitFinalResult("selection_underscore"));
        assertThatEndsWith("is _SOME_ word");
    }

    @Test
    public void test33() {
        assertTrue(mEditor.commitFinalResult("test word1"));
        assertTrue(mEditor.addSpace());
        assertTrue(mEditor.commitFinalResult("word2"));
        assertThat(getTextBeforeCursor(11), is("word1 word2"));
        assertTrue(mEditor.cutAll());
        assertThat(getTextBeforeCursor(1), is(""));
        assertTrue(mEditor.paste());
        assertThat(getTextBeforeCursor(11), is("word1 word2"));
    }

    private String getTextBeforeCursor(int n) {
        return mEditor.getInputConnection().getTextBeforeCursor(n, 0).toString();
    }

    private void assertThatEndsWith(String str) {
        assertTrue(mEditor.goToEnd());
        assertThat(getTextBeforeCursor(str.length()), is(str));
    }
}