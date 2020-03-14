package ee.ioc.phon.android.speechutils.editor;

import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Command {

    private static final String[] EMPTY_ARRAY = new String[0];

    private final static String SEPARATOR = "<___>";
    private final String mLabel;
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
     * @param label       short label for GUI
     * @param comment     free-form comment
     * @param locale      locale of the utterance
     * @param service     regular expression to match the recognizer service class name
     * @param app         regular expression to match the calling app package name
     * @param utt         regular expression with capturing groups to match the utterance
     * @param replacement replacement string for the matched substrings, typically empty in case of commands
     * @param id          name of the command to execute, null if missing
     * @param args        arguments of the command
     */
    public Command(String label, String comment, Pattern locale, Pattern service, Pattern app, Pattern utt, String replacement, String id, String[] args) {
        mLabel = label;
        mComment = comment;
        mLocale = locale;
        mService = service;
        mApp = app;
        mUtt = utt;
        mReplacement = replacement == null ? "" : replacement;
        mCommand = id;
        if (args == null) {
            mArgs = EMPTY_ARRAY;
        } else {
            mArgs = args;
        }
        mArgsAsStr = TextUtils.join(SEPARATOR, mArgs);
    }

    public Command(String label, String comment, Pattern locale, Pattern service, Pattern app, Pattern utt, String replacement, String id) {
        this(label, comment, locale, service, app, utt, replacement, id, null);
    }


    public Command(String utt, String replacement, String id, String[] args) {
        this(null, null, null, null, null, Pattern.compile(utt, Constants.REWRITE_PATTERN_FLAGS), replacement, id, args);
    }

    public Command(String utt, String replacement, String id) {
        this(null, null, null, null, null, Pattern.compile(utt, Constants.REWRITE_PATTERN_FLAGS), replacement, id, null);
    }

    public Command(String utt, String replacement) {
        this(null, null, null, null, null, Pattern.compile(utt, Constants.REWRITE_PATTERN_FLAGS), replacement, null, null);
    }

    public String getLabel() {
        return mLabel;
    }

    public String getComment() {
        return mComment;
    }

    public Pattern getUtterance() {
        return mUtt;
    }

    @NonNull
    public String getReplacement() {
        return mReplacement;
    }

    public String getId() {
        return mCommand;
    }

    public String[] getArgs() {
        return mArgs;
    }

    /**
     * TODO: experimental
     *
     * @param colId Column name
     * @return field value from the given column, converted to String
     */
    public String get(@NonNull String colId) {
        switch (colId) {
            case UtteranceRewriter.HEADER_LABEL:
                return mLabel;
            case UtteranceRewriter.HEADER_COMMENT:
                return mComment;
            case UtteranceRewriter.HEADER_LOCALE:
                return mLocale.pattern();
            case UtteranceRewriter.HEADER_SERVICE:
                return mService.pattern();
            case UtteranceRewriter.HEADER_APP:
                return mApp.pattern();
            case UtteranceRewriter.HEADER_UTTERANCE:
                return unre(mUtt.pattern());
            case UtteranceRewriter.HEADER_REPLACEMENT:
                return mReplacement;
            case UtteranceRewriter.HEADER_COMMAND:
                return mCommand;
            case UtteranceRewriter.HEADER_ARG1:
                if (mArgs.length > 0) {
                    return mArgs[0];
                }
                break;
            case UtteranceRewriter.HEADER_ARG2:
                if (mArgs.length > 1) {
                    return mArgs[1];
                }
                break;
            default:
                break;
        }
        return null;
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
        // TODO: rethink this: we could match a sub string and do something with the
        // prefix and suffix
        if (m.matches()) {
            if (mArgsAsStr.isEmpty()) {
                argsEvaluated = EMPTY_ARRAY;
            } else {
                try {
                    argsEvaluated = TextUtils.split(m.replaceAll(mArgsAsStr), SEPARATOR);
                } catch (IndexOutOfBoundsException e) {
                    // TODO: rethink this hack; occurs in Matcher.group, e.g. when "No group 1"
                    argsEvaluated = new String[]{e.getLocalizedMessage()};
                }
            }
        }
        try {
            return new Pair<>(m.replaceAll(mReplacement), argsEvaluated);
        } catch (ArrayIndexOutOfBoundsException e) {
            // This happens if the replacement references a group that does not exist
            // TODO: throw an exception
            return new Pair<>("[ERROR: " + e.getLocalizedMessage() + "]", argsEvaluated);
        }
    }

    public Map<String, String> toMap(Collection<String> header) {
        Map<String, String> map = new HashMap<>();
        for (String colName : header) {
            switch (colName) {
                case UtteranceRewriter.HEADER_LABEL:
                    map.put(UtteranceRewriter.HEADER_LABEL, mLabel);
                    break;
                case UtteranceRewriter.HEADER_COMMENT:
                    map.put(UtteranceRewriter.HEADER_COMMENT, mComment);
                    break;
                case UtteranceRewriter.HEADER_LOCALE:
                    map.put(UtteranceRewriter.HEADER_LOCALE, mLocale.pattern());
                    break;
                case UtteranceRewriter.HEADER_SERVICE:
                    map.put(UtteranceRewriter.HEADER_SERVICE, mService.pattern());
                    break;
                case UtteranceRewriter.HEADER_APP:
                    map.put(UtteranceRewriter.HEADER_APP, mApp.pattern());
                    break;
                case UtteranceRewriter.HEADER_UTTERANCE:
                    map.put(UtteranceRewriter.HEADER_UTTERANCE, mUtt.pattern());
                    break;
                case UtteranceRewriter.HEADER_REPLACEMENT:
                    map.put(UtteranceRewriter.HEADER_REPLACEMENT, mReplacement);
                    break;
                case UtteranceRewriter.HEADER_COMMAND:
                    map.put(UtteranceRewriter.HEADER_COMMAND, mCommand);
                    break;
                case UtteranceRewriter.HEADER_ARG1:
                    if (mArgs.length > 0) {
                        map.put(UtteranceRewriter.HEADER_ARG1, mArgs[0]);
                    }
                    break;
                case UtteranceRewriter.HEADER_ARG2:
                    if (mArgs.length > 1) {
                        map.put(UtteranceRewriter.HEADER_ARG2, mArgs[1]);
                    }
                    break;
                default:
                    break;
            }
        }
        return map;
    }

    // TODO: simplify to accept List<String> as input (because keys are not used)
    public String toTsv(SortedMap<Integer, String> header) {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (SortedMap.Entry<Integer, String> entry : header.entrySet()) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append('\t');
            }
            switch (entry.getValue()) {
                case UtteranceRewriter.HEADER_LABEL:
                    sb.append(escape(mLabel));
                    break;
                case UtteranceRewriter.HEADER_COMMENT:
                    sb.append(escape(mComment));
                    break;
                case UtteranceRewriter.HEADER_LOCALE:
                    sb.append(escape(mLocale));
                    break;
                case UtteranceRewriter.HEADER_SERVICE:
                    sb.append(escape(mService));
                    break;
                case UtteranceRewriter.HEADER_APP:
                    sb.append(escape(mApp));
                    break;
                case UtteranceRewriter.HEADER_UTTERANCE:
                    sb.append(escape(mUtt));
                    break;
                case UtteranceRewriter.HEADER_REPLACEMENT:
                    sb.append(escape(mReplacement));
                    break;
                case UtteranceRewriter.HEADER_COMMAND:
                    sb.append(escape(mCommand));
                    break;
                case UtteranceRewriter.HEADER_ARG1:
                    if (mArgs.length > 0) {
                        sb.append(escape(mArgs[0]));
                    }
                    break;
                case UtteranceRewriter.HEADER_ARG2:
                    if (mArgs.length > 1) {
                        sb.append(escape(mArgs[1]));
                    }
                    break;
                default:
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * TODO: where and why should one use this?
     */
    public String toString() {
        if (mCommand == null) {
            return mUtt + "/" + mReplacement;
        }
        return mUtt + "/" + mReplacement + "/" + mCommand + "(" + mArgsAsStr + ")";
    }

    /**
     * True if the given command is equal to this command. Here the equality
     * only considers the replacement, and (if defined) the command ID and its arguments.
     * This means that the activation pattern (utterance), context restrictions (command matcher),
     * and labels and comments are ignored when testing the equality.
     */
    public boolean equalsCommand(@NonNull Command that) {
        if (getId() == null) {
            return getReplacement().equals(that.getReplacement());
        }
        return getReplacement().equals(that.getReplacement()) &&
                getId().equals(that.getId()) &&
                Arrays.equals(getArgs(), that.getArgs());
    }

    /**
     * Work in progress.
     * Map the Utterance-field (regex) to a string that is matched by this regex.
     * TODO: return an iterator over all possible matches
     */
    public String makeUtt() {
        String val = unre(mUtt.pattern());
        if (Pattern.matches(val, val)) {
            return val;
        }
        return null;
    }

    public String getLabelOrCommentOrString() {
        String label = getLabel();
        if (label == null) {
            label = getComment();
        }
        if (label == null) {
            label = toString();
        }
        return label;
    }

    /**
     * Removes ^ and $ from the given regex
     * TODO: experimental
     */
    private static String unre(String re) {
        if (re.startsWith("^")) re = re.substring(1);
        if (re.endsWith("$")) re = re.substring(0, re.length() - 1);
        return re;
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
}