package ee.ioc.phon.android.speechutils.editor;

import android.text.TextUtils;
import android.util.Pair;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Command {
    private final static String SEPARATOR = "___";
    private final Pattern mPattern;
    private final String mReplacement;
    private final String mId;
    private final String[] mArgs;
    private final String mArgsAsStr;

    /**
     * @param pattern     regular expression with capturing groups
     * @param replacement replacement string for the matched substrings, typically empty in case of commands
     * @param id          name of the command to execute, null if missing
     * @param args        arguments of the command
     */
    public Command(Pattern pattern, String replacement, String id, String[] args) {
        mPattern = pattern;
        mReplacement = replacement;
        mId = id;
        if (args == null) {
            mArgs = new String[0];
        } else {
            mArgs = args;
        }
        mArgsAsStr = TextUtils.join(SEPARATOR, mArgs);
    }

    public Command(String pattern, String replacement, String id, String[] args) {
        this(Pattern.compile(pattern), replacement, id, args);
    }

    public String getId() {
        return mId;
    }

    public Pattern getPattern() {
        return mPattern;
    }

    public String getReplacement() {
        return mReplacement;
    }

    public String[] getArgs() {
        return mArgs;
    }

    private Matcher matcher(CharSequence str) {
        return mPattern.matcher(str);
    }

    public Pair<String, String[]> match(CharSequence str) {
        Matcher m = matcher(str);
        if (m.matches()) {
            String newStr = m.replaceAll(mReplacement);
            String[] argsEvaluated = TextUtils.split(m.replaceAll(mArgsAsStr), SEPARATOR);
            return new Pair<>(newStr, argsEvaluated);
        }
        return null;
    }

    public String toString() {
        return mPattern + "/" + mReplacement + "/" + mId + "(" + mArgs + ")";
    }
}