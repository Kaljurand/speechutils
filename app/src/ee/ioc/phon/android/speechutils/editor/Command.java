package ee.ioc.phon.android.speechutils.editor;

import android.text.TextUtils;
import android.util.Pair;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Command {
    Pattern mPattern;
    String mReplacement;
    String mId;
    List<String> mArgs;
    String mArgsAsStr;

    /**
     * @param pattern     regular expression with capturing groups
     * @param replacement replacement string for the matched substrings, typically empty in case of commands
     * @param id          name of the command to execute, null if missing
     * @param args        arguments of the command
     */
    public Command(Pattern pattern, String replacement, String id, List<String> args) {
        mPattern = pattern;
        mReplacement = replacement;
        mId = id;
        mArgs = args;
        mArgsAsStr = TextUtils.join("---", args);
    }

    public Command(String pattern, String replacement, String id, List<String> args) {
        this(Pattern.compile(pattern), replacement, id, args);
    }

    public String getId() {
        return mId;
    }

    public Matcher matcher(CharSequence input) {
        return mPattern.matcher(input);
    }

    public Pair<String, String[]> match(CharSequence str) {
        Matcher m = matcher(str);
        if (m.matches()) {
            String newStr = m.replaceAll(mReplacement);
            String[] argsEvaluated = TextUtils.split(m.replaceAll(mArgsAsStr), "---");
            return new Pair<>(newStr, argsEvaluated);
        }
        return null;
    }

    public String toString() {
        return mPattern + "/" + mReplacement + "/" + mId + "(" + mArgs + ")";
    }
}