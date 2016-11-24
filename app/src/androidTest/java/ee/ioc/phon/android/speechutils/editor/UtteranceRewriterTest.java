package ee.ioc.phon.android.speechutils.editor;

import android.support.test.runner.AndroidJUnit4;
import android.util.Pair;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
public class UtteranceRewriterTest {

    private static final List<Command> COMMANDS;

    static {
        List<Command> list = new ArrayList<>();
        list.add(new Command("s/(.*)/(.*)/", "X", "replace", new String[]{"$1", "$2"}));
        // Map <1><2><3> into 1, 2, 3
        // TODO: is there a better way
        list.add(new Command("<([^>]+)>", "$1, "));
        list.add(new Command(", K6_STOP, ", "<K6_STOP>"));
        COMMANDS = Collections.unmodifiableList(list);
    }

    private UtteranceRewriter mUr;

    @Before
    public void before() {
        mUr = new UtteranceRewriter(COMMANDS);
    }

    @Test
    public void test01() {
        Command command = new Command("s/(.*)/(.*)/", "X", "replace", new String[]{"$1", "$2"});
        assertThat(command.toTsv(), is("\t\t\t\ts/(.*)/(.*)/\tX\treplace\t$1\t$2"));
        Pair<String, String[]> pair = command.parse("s/_/a/");
        assertThat(pair.first, is("X"));
        assertThat(pair.second[0], is("_"));
        assertThat(pair.second[1], is("a"));
    }

    @Test
    public void test02() {
        rewrite("s/_/a/", "replace(_,a)", "X");
    }

    @Test
    public void test03() {
        rewrite("<1><2><3><K6_STOP>", "null()", "1, 2, 3<K6_STOP>");
    }

    private void rewrite(String str1, String str2) {
        UtteranceRewriter.Rewrite rewrite = mUr.getRewrite(str1);
        assertThat(rewrite.toString(), is(str2));
    }

    private void rewrite(String str1, String str2, String str3) {
        UtteranceRewriter.Rewrite rewrite = mUr.getRewrite(str1);
        assertThat(rewrite.toString(), is(str2));
        assertThat(rewrite.mStr, is(str3));
    }

}