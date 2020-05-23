package ee.ioc.phon.android.speechutils.editor;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class CommandTest {

    Command c1 = new Command("c1", "", null, null, null, Pattern.compile("^c1$"), "", "replaceSel", new String[]{"c"});
    Command c2 = new Command("c2", "", null, null, null, Pattern.compile("^c2$"), "", "replaceSel", new String[]{"c"});
    Command c3 = new Command("c3", "", null, null, null, Pattern.compile("^c3$"), "", "replaceSel", new String[]{"c3"});

    @Test
    public void test01() {
        assertTrue(c1.equalsCommand(c2));
    }

    @Test
    public void test02() {
        assertFalse(c1.equalsCommand(c3));
    }

}