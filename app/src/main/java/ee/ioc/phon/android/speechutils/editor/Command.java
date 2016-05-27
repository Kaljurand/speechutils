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

    public Command(String pattern, String replacement, String id) {
        this(Pattern.compile(pattern), replacement, id, null);
    }

    public Command(String pattern, String replacement) {
        this(Pattern.compile(pattern), replacement, null, null);
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

    /**
     * Rewrites the given string and extracts the arguments if the string
     * corresponds to the command (i.e. if the entire string matches the pattern).
     *
     * @param str string to be matched
     * @return pair of replacement and array of arguments
     */
    public Pair<String, String[]> match(CharSequence str) {
        Matcher m = mPattern.matcher(str);
        String newStr = m.replaceAll(mReplacement);
        String[] argsEvaluated = null;
        // If the entire region matches then we evaluate the arguments as well
        if (m.matches()) {
            argsEvaluated = TextUtils.split(m.replaceAll(mArgsAsStr), SEPARATOR);
        }
        return new Pair<>(newStr, argsEvaluated);
    }

    public String toString() {
        return mPattern + "/" + mReplacement + "/" + mId + "(" + mArgs + ")";
    }
}