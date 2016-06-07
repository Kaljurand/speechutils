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
        list.add(new Command("select (.*)", "", "select", new String[]{"$1"}));
        list.add(new Command("selection_replace (.*)", "", "replaceSel", new String[]{"$1"}));
        list.add(new Command("selection_underscore", "", "replaceSel", new String[]{"_{}_"}));
        list.add(new Command("selection_quote", "", "replaceSel", new String[]{"\"{}\""}));
        list.add(new Command("selection_double", "", "replaceSel", new String[]{"{}{}"}));
        list.add(new Command("selection_inc", "", "incSel"));
        list.add(new Command("selection_uc", "", "ucSel"));
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
        assertNotNull(mEditor.commitFinalResult("start12345 67890"));
        assertThatEndsWith("67890");
        assertTrue(mEditor.deleteLeftWord());
        assertThatEndsWith("12345");
        assertTrue(mEditor.delete("12345"));
        assertThatEndsWith("Start");
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
        assertNotNull(mEditor.commitFinalResult("...1245"));
        assertTrue(mEditor.goToCharacterPosition(4));
        assertThat(getTextBeforeCursor(10), is("...1"));
    }

    @Test
    public void test05() {
        assertNotNull(mEditor.commitFinalResult("a12345 67890_12345"));
        assertTrue(mEditor.select("12345"));
        assertThat(getTextBeforeCursor(2), is("0_"));
        assertTrue(mEditor.deleteLeftWord());
        assertThatEndsWith("0_");
        assertTrue(mEditor.deleteLeftWord());
        assertThatEndsWith("45");
    }

    @Test
    public void test06() {
        assertNotNull(mEditor.commitFinalResult("a12345 67890_12345"));
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
        assertNotNull(mEditor.commitFinalResult("123456789"));
        assertTrue(mEditor.goToCharacterPosition(2));
        assertThat(getTextBeforeCursor(2), is("12"));
        assertThatEndsWith("89");
    }

    @Test
    public void test08() {
        assertNotNull(mEditor.commitFinalResult("old_word"));
        assertThatEndsWith("New_word");
    }

    @Test
    public void test09() {
        assertNotNull(mEditor.commitFinalResult("r2_old"));
        assertThatEndsWith("R2_new");
    }

    @Test
    public void test10() {
        assertNotNull(mEditor.commitFinalResult("test old_word test"));
        assertThatEndsWith("new_word test");
    }

    @Test
    public void test11() {
        assertNotNull(mEditor.commitFinalResult("test old_word"));
        assertNotNull(mEditor.commitFinalResult("s/old_word/new_word/"));
        assertThatEndsWith("new_word");
    }

    @Test
    public void test12() {
        assertNotNull(mEditor.commitFinalResult("test word1 word2"));
        assertNotNull(mEditor.commitFinalResult("connect word1 and word2"));
        assertThatEndsWith("word1-word2");
    }

    @Test
    public void test12_1() {
        assertNotNull(mEditor.commitFinalResult("test word1 word2"));
        assertTrue(mEditor.commitPartialResult("connect word1 and word2"));
        assertNotNull(mEditor.commitFinalResult("connect word1 and word2"));
        assertThatEndsWith("word1-word2");
    }

    @Test
    public void test13() {
        assertNotNull(mEditor.commitFinalResult("test word1 word2"));
        assertNotNull(mEditor.commitFinalResult("connect word1"));
        assertNotNull(mEditor.commitFinalResult("and"));
        assertNotNull(mEditor.commitFinalResult("word2"));
        assertThat(getTextBeforeCursor(11), is("word1-word2"));
        // TODO:
        //assertThat(mEditor.getText().toString(), is("test word1-word2"));
    }

    @Test
    public void test14() {
        assertNotNull(mEditor.commitFinalResult("test word1"));
        assertTrue(mEditor.addSpace());
        assertNotNull(mEditor.commitFinalResult("word2"));
        assertThatEndsWith("word1 word2");
        assertNotNull(mEditor.commitFinalResult("connect word1 and word2"));
        assertThatEndsWith("word1-word2");
    }

    @Test
    public void test15() {
        assertNotNull(mEditor.commitFinalResult("test word1"));
        assertTrue(mEditor.addSpace());
        assertNotNull(mEditor.commitFinalResult("word2"));
        assertThat(getTextBeforeCursor(11), is("word1 word2"));
        assertTrue(mEditor.deleteAll());
        assertThat(getTextBeforeCursor(1), is(""));
    }

    /**
     * If command does not fully match then its replacement is ignored.
     */
    @Test
    public void test16() {
        assertNotNull(mEditor.commitFinalResult("I will delete something"));
        assertThatEndsWith("something");
    }

    @Test
    public void test17() {
        assertNotNull(mEditor.commitFinalResult("there are word1 and word2..."));
        assertNotNull(mEditor.commitFinalResult("select word1 and word2"));
        assertTrue(mEditor.goToEnd());
        assertThatEndsWith("word2...");
    }

    @Test
    public void test18() {
        assertNotNull(mEditor.commitFinalResult("there are word1 and word2..."));
        assertNotNull(mEditor.commitFinalResult("select word1 and word2"));
        assertNotNull(mEditor.commitFinalResult("selection_replace REPL"));
        assertThatEndsWith("re REPL...");
    }

    @Test
    public void test19() {
        assertNotNull(mEditor.commitFinalResult("there are word1 and word2..."));
        assertNotNull(mEditor.commitFinalResult("select word1 and word2"));
        assertNotNull(mEditor.commitFinalResult("selection_underscore"));
        assertThatEndsWith("are _word1 and word2_...");
    }

    @Test
    public void test20() {
        assertNotNull(mEditor.commitFinalResult("a"));
        assertNotNull(mEditor.commitFinalResult("select a"));
        assertNotNull(mEditor.commitFinalResult("selection_double"));
        // TODO: maybe keep the selection after the command
        assertNotNull(mEditor.commitFinalResult("selection_double"));
        assertTrue(mEditor.goToEnd());
        assertThat(getTextBeforeCursor(5), is("AA"));
    }

    @Test
    public void test21() {
        assertNotNull(mEditor.commitFinalResult("123456789"));
        assertNotNull(mEditor.commitFinalResult("select 3"));
        assertNotNull(mEditor.commitFinalResult("selection_inc"));
        assertTrue(mEditor.goForward(3));
        assertNotNull(mEditor.commitFinalResult("select 5"));
        assertNotNull(mEditor.commitFinalResult("selection_inc"));
        assertThatEndsWith("124466789");
    }

    @Test
    public void test22() {
        assertNotNull(mEditor.commitFinalResult("this is some word"));
        assertNotNull(mEditor.commitFinalResult("select is some"));
        assertNotNull(mEditor.commitFinalResult("selection_uc"));
        assertThatEndsWith("his IS SOME word");
    }

    @Test
    public void test23() {
        assertNotNull(mEditor.commitFinalResult("this is some word"));
        assertTrue(mEditor.selectAll());
        assertNotNull(mEditor.commitFinalResult("selection_replace REPL"));
        assertThat(mEditor.getText().toString(), is("REPL"));
    }

    @Test
    public void test24() {
        assertNotNull(mEditor.commitFinalResult("test word1 word2"));
        assertNotNull(mEditor.commitFinalResult("connect word1 and not_exist"));
        assertThatTextIs("Test word1 word2");
    }

    @Test
    public void test25() {
        assertNotNull(mEditor.commitFinalResult("test word1 word2"));
        assertNotNull(mEditor.undo());
        assertThatTextIs("");
    }

    @Test
    public void test30() {
        assertNotNull(mEditor.commitFinalResult("there are word1 and word2..."));
        assertNotNull(mEditor.commitFinalResult("select word1 and word2"));
        assertNotNull(mEditor.commitFinalResult("selection_uc"));
        assertTrue(mEditor.goToEnd());
        assertNotNull(mEditor.commitFinalResult("select word1 and word2"));
        assertNotNull(mEditor.commitFinalResult("selection_quote"));
        assertThatEndsWith("are \"WORD1 AND WORD2\"...");
    }

    /**
     * TODO: incorrectly replaces with "_some_" instead of "_SOME_"
     */
    //@Test
    public void test31() {
        assertNotNull(mEditor.commitFinalResult("this is SOME word"));
        assertNotNull(mEditor.commitFinalResult("underscore some"));
        assertThatEndsWith("_SOME_ word");
    }

    /**
     * Same as before but using selection.
     */
    @Test
    public void test32() {
        assertNotNull(mEditor.commitFinalResult("this is SOME word"));
        assertNotNull(mEditor.commitFinalResult("select some"));
        assertNotNull(mEditor.commitFinalResult("selection_underscore"));
        assertThatEndsWith("is _SOME_ word");
    }

    /**
     * TODO: Can't create handler inside thread that has not called Looper.prepare()
     */
    //@Test
    public void test33() {
        assertNotNull(mEditor.commitFinalResult("test word1"));
        assertTrue(mEditor.addSpace());
        assertNotNull(mEditor.commitFinalResult("word2"));
        assertThatEndsWith("word1 word2");
        assertTrue(mEditor.cutAll());
        assertThat(getTextBeforeCursor(1), is(""));
        assertTrue(mEditor.paste());
        assertThatEndsWith("word1 word2");
    }

    private String getTextBeforeCursor(int n) {
        return mEditor.getInputConnection().getTextBeforeCursor(n, 0).toString();
    }

    private void assertThatEndsWith(String str) {
        assertTrue(mEditor.goToEnd());
        assertThat(getTextBeforeCursor(str.length()), is(str));
    }

    private void assertThatTextIs(String str) {
        assertThat(mEditor.getText().toString(), is(str));
    }
}