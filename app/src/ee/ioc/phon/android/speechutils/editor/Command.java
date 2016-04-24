package ee.ioc.phon.android.speechutils.editor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Command {
    Pattern mPattern;
    String mReplacement;
    String mId;
    List<String> mArgs;

    public Command(Pattern pattern, String replacement, String id, List<String> args) {
        mPattern = pattern;
        mReplacement = unescape(replacement);
        mId = id;
        mArgs = args;
    }

    public Command(String pattern, String replacement, String id, List<String> args) {
        this(Pattern.compile(unescape(pattern)), replacement, id, args);
    }

    public Pattern getPattern() {
        return mPattern;
    }

    public String getId() {
        return mId;
    }

    public Matcher matcher(CharSequence input) {
        return mPattern.matcher(input);
    }

    public String toString() {
        return mPattern + "/" + mReplacement + "/" + mId + "(" + mArgs + ")";
    }

    /**
     * Maps literals of the form "\n" and "\t" to newlines and tabs.
     */
    private static String unescape(String str) {
        return str.replace("\\n", "\n").replace("\\t", "\t");
    }
}
