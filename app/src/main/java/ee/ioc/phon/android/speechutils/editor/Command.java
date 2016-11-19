package ee.ioc.phon.android.speechutils.editor;

import android.text.TextUtils;
import android.util.Pair;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Command {
    private final static String SEPARATOR = "<___>";
    private final String mComment;
    private final Pattern mLocale;
    private final Pattern mService;
    private final Pattern mApp;
    private final Pattern mUtt;
    private final String mReplacement;
    private final String mCommand;
    private final String[] mArgs;
    private final String mArgsAsStr;

    /**
     * @param comment     free-form comment
     * @param locale      locale of the utterance
     * @param service     regular expression to match the recognizer service class name
     * @param app         regular expression to match the calling app package name
     * @param utt         regular expression with capturing groups to match the utterance
     * @param replacement replacement string for the matched substrings, typically empty in case of commands
     * @param id          name of the command to execute, null if missing
     * @param args        arguments of the command
     */
    public Command(String comment, Pattern locale, Pattern service, Pattern app, Pattern utt, String replacement, String id, String[] args) {
        mComment = comment;
        mLocale = locale;
        mService = service;
        mApp = app;
        mUtt = utt;
        mReplacement = replacement;
        mCommand = id;
        if (args == null) {
            mArgs = new String[0];
        } else {
            mArgs = args;
        }
        mArgsAsStr = TextUtils.join(SEPARATOR, mArgs);
    }

    public Command(String comment, Pattern locale, Pattern service, Pattern app, Pattern utt, String replacement, String id) {
        this(comment, locale, service, app, utt, replacement, id, null);
    }


    public Command(String utt, String replacement, String id, String[] args) {
        this(null, null, null, null, Pattern.compile(utt, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), replacement, id, args);
    }

    public Command(String utt, String replacement, String id) {
        this(null, null, null, null, Pattern.compile(utt, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), replacement, id, null);
    }

    public Command(String utt, String replacement) {
        this(null, null, null, null, Pattern.compile(utt, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), replacement, null, null);
    }

    public String getId() {
        return mCommand;
    }

    public String[] getArgs() {
        return mArgs;
    }

    /**
     * Parses the given string.
     * If the entire string matches the utterance pattern, then extracts the arguments as well.
     * Example:
     * str = replace A with B
     * mUtt = replace (.*) with (.*)
     * mReplacement = ""
     * $1<___>$2
     * A<___>B
     * m.replaceAll(mReplacement) = ""
     * argsEvaluated = [A, B]
     *
     * @param str string to be matched
     * @return pair of replacement and array of arguments
     */
    public Pair<String, String[]> parse(CharSequence str) {
        Matcher m = mUtt.matcher(str);
        String[] argsEvaluated = null;
        // If the entire region matches then we evaluate the arguments as well
        if (!mArgsAsStr.isEmpty() && m.matches()) {
            argsEvaluated = TextUtils.split(m.replaceAll(mArgsAsStr), SEPARATOR);
        }
        try {
            return new Pair<>(m.replaceAll(mReplacement), argsEvaluated);
        } catch (ArrayIndexOutOfBoundsException e) {
            // This happens if the replacement references a group that does not exist
            // TODO: throw an exception
            return new Pair<>("[ERROR: " + e.getLocalizedMessage() + "]", argsEvaluated);
        }
    }

    /**
     * Pretty-prints the command by putting the components of the command onto separate lines,
     * and marks spaces with middot in every component except for the human-readable comment and the
     * command ID (which does not contain spaces). The human-readable comment is only shown if it
     * is non-empty.
     *
     * @return pretty-printed command
     */
    public String toPp() {
        String str = pp(mLocale) + '\n' +
                pp(mUtt) + '\n' +
                pp(mReplacement);
        if (mCommand != null) {
            str += '\n' + mCommand;
        }
        for (String arg : mArgs) {
            str += '\n' + pp(arg);
        }
        str += '\n' + pp(mService) + '\n' + pp(mApp);
        if (mComment != null && !mComment.isEmpty()) {
            return str + '\n' + mComment;
        }
        return str;
    }

    public String toTsv() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(escape(mComment));
        stringBuilder.append('\t');
        stringBuilder.append(escape(mLocale));
        stringBuilder.append('\t');
        stringBuilder.append(escape(mService));
        stringBuilder.append('\t');
        stringBuilder.append(escape(mApp));
        stringBuilder.append('\t');
        stringBuilder.append(escape(mUtt));
        stringBuilder.append('\t');
        stringBuilder.append(escape(mReplacement));
        if (getId() != null) {
            stringBuilder.append('\t');
            stringBuilder.append(escape(mCommand));
        }
        for (String arg : getArgs()) {
            stringBuilder.append('\t');
            stringBuilder.append(escape(arg));
        }
        return stringBuilder.toString();
    }

    public String toString() {
        return mUtt + "/" + mReplacement + "/" + mCommand + "(" + mArgsAsStr + ")";
    }

    private static String pp(Object str) {
        return escape(str).replace(" ", "·");
    }

    /**
     * Maps newlines and tabs to literals of the form "\n" and "\t".
     */
    private static String escape(Object str) {
        if (str == null) {
            return "";
        }
        return str.toString().replace("\n", "\\n").replace("\t", "\\t");
    }

    /**
     * Maps literals of the form "\n" and "\t" to newlines and tabs.
     */
    public static String unescape(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\n", "\n").replace("\\t", "\t");
    }

    public static Command createEmptyCommand(String comment) {
        return new Command(comment, null, null, null, null, null, null, null);
    }
}