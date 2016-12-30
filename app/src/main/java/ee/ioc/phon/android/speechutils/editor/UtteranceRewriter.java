package ee.ioc.phon.android.speechutils.editor;

import android.content.ContentResolver;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class UtteranceRewriter {

    private static final Pattern PATTERN_TRAILING_TABS = Pattern.compile("\t*$");

    public static final String HEADER_COMMENT = "Comment";
    public static final String HEADER_LOCALE = "Locale";
    public static final String HEADER_SERVICE = "Service";
    public static final String HEADER_APP = "App";
    public static final String HEADER_UTTERANCE = "Utterance";
    public static final String HEADER_REPLACEMENT = "Replacement";
    public static final String HEADER_COMMAND = "Command";
    public static final String HEADER_ARG1 = "Arg1";
    public static final String HEADER_ARG2 = "Arg2";

    private static final Set<String> COLUMNS;

    static {
        Set<String> aSet = new HashSet<>();
        aSet.add(HEADER_COMMENT);
        aSet.add(HEADER_LOCALE);
        aSet.add(HEADER_SERVICE);
        aSet.add(HEADER_APP);
        aSet.add(HEADER_UTTERANCE);
        aSet.add(HEADER_REPLACEMENT);
        aSet.add(HEADER_COMMAND);
        aSet.add(HEADER_ARG1);
        aSet.add(HEADER_ARG2);
        COLUMNS = Collections.unmodifiableSet(aSet);
    }

    protected static class Rewrite {
        public final String mId;
        public final String mStr;
        public final String[] mArgs;

        public Rewrite(String id, String str, String[] args) {
            mId = id;
            mStr = str;
            mArgs = args;
        }

        public boolean isCommand() {
            return mId != null;
        }

        public String toString() {
            if (mArgs == null) {
                return mId + "()";
            }
            return mId + "(" + TextUtils.join(",", mArgs) + ")";
        }
    }

    private static class CommandHolder {
        private final List<Command> mCommands;
        private final String[] mHeader;
        private final Map<Integer, String> mErrors = new HashMap<>();

        public CommandHolder(String[] header) {
            this(header, new ArrayList<Command>());
        }

        public CommandHolder(String[] inputHeader, List<Command> commands) {
            List<String> header = new ArrayList<>();
            for (String columnName : inputHeader) {
                if (COLUMNS.contains(columnName)) {
                    header.add(columnName);
                }
            }
            mHeader = header.toArray(new String[header.size()]);
            mCommands = commands;
        }

        public String[] getHeader() {
            return mHeader;
        }

        public List<Command> getCommands() {
            return mCommands;
        }

        public Map<Integer, String> getErrors() {
            return mErrors;
        }

        public boolean addCommand(Command command) {
            return mCommands.add(command);
        }

        public String addError(int lineNumber, String message) {
            return mErrors.put(lineNumber, message);
        }

        public int size() {
            return mCommands.size();
        }
    }

    private final CommandHolder mCommandHolder;

    public UtteranceRewriter(CommandHolder commandHolder) {
        mCommandHolder = commandHolder;
    }

    public UtteranceRewriter(String str, CommandMatcher commandMatcher) {
        this(loadRewrites(str, commandMatcher));
    }

    public UtteranceRewriter(String str, String[] header, CommandMatcher commandMatcher) {
        this(loadRewrites(str, header, commandMatcher));
    }

    public UtteranceRewriter(String str, String[] header) {
        this(str, header, null);
    }

    public UtteranceRewriter(String str) {
        this(str, (CommandMatcher) null);
    }

    public UtteranceRewriter(ContentResolver contentResolver, Uri uri) throws IOException {
        this(loadRewrites(contentResolver, uri));
    }

    /**
     * Rewrites and returns the given string,
     * and the first matching command.
     */
    public Rewrite getRewrite(String str) {
        for (Command command : mCommandHolder.getCommands()) {
            Pair<String, String[]> pair = command.parse(str);
            String commandId = command.getId();
            if (commandId == null) {
                str = pair.first;
            } else if (pair.second != null) {
                // If there is a full match (pair.second != null) and there is a command (commandId != null)
                // then stop the search and return the command.
                str = pair.first;
                return new Rewrite(commandId, str, pair.second);
            }
        }
        return new Rewrite(null, str, null);
    }

    /**
     * Rewrites and returns the given results.
     */
    public List<String> rewrite(List<String> results) {
        List<String> rewrittenResults = new ArrayList<>();
        for (String result : results) {
            rewrittenResults.add(getRewrite(result).mStr);
        }
        return rewrittenResults;
    }

    public String rewrite(String result) {
        return getRewrite(result).mStr;
    }

    /**
     * Serializes the rewrites as tab-separated-values.
     */
    public String toTsv() {
        StringBuilder stringBuilder = new StringBuilder();
        String[] header = mCommandHolder.getHeader();
        stringBuilder.append(TextUtils.join("\t", header));
        for (Command command : mCommandHolder.getCommands()) {
            stringBuilder.append('\n');
            stringBuilder.append(command.toTsv(header));
        }
        return stringBuilder.toString();
    }

    public String[] toStringArray() {
        String[] header = mCommandHolder.getHeader();
        String[] array = new String[mCommandHolder.size()];
        int i = 0;
        for (Command command : mCommandHolder.getCommands()) {
            array[i++] = command.toPp(header);
        }
        return array;
    }

    public String[] getErrorsAsStringArray() {
        Map<Integer, String> errors = mCommandHolder.getErrors();
        String[] array = new String[errors.size()];
        int i = 0;
        for (Map.Entry<Integer, String> entry : errors.entrySet()) {
            array[i++] = "line " + entry.getKey() + ": " + entry.getValue();
        }
        return array;
    }

    /**
     * Pretty-prints the string returned by the server to be orthographically correct (Estonian),
     * assuming that the string represents a sequence of tokens separated by a single space character.
     * Note that a text editor (which has additional information about the context of the cursor)
     * will need to do additional pretty-printing, e.g. capitalization if the cursor follows a
     * sentence end marker.
     *
     * @param str String to be pretty-printed
     * @return Pretty-printed string (never null)
     */
    public static String prettyPrint(String str) {
        boolean isSentenceStart = false;
        boolean isWhitespaceBefore = false;
        String text = "";
        for (String tok : str.split(" ")) {
            if (tok.length() == 0) {
                continue;
            }
            String glue = " ";
            char firstChar = tok.charAt(0);
            if (isWhitespaceBefore
                    || Constants.CHARACTERS_WS.contains(firstChar)
                    || Constants.CHARACTERS_PUNCT.contains(firstChar)) {
                glue = "";
            }

            if (isSentenceStart) {
                tok = Character.toUpperCase(firstChar) + tok.substring(1);
            }

            if (text.length() == 0) {
                text = tok;
            } else {
                text += glue + tok;
            }

            isWhitespaceBefore = Constants.CHARACTERS_WS.contains(firstChar);

            // If the token is not a character then we are in the middle of the sentence.
            // If the token is an EOS character then a new sentences has started.
            // If the token is some other character other than whitespace (then we are in the
            // middle of the sentences. (The whitespace characters are transparent.)
            if (tok.length() > 1) {
                isSentenceStart = false;
            } else if (Constants.CHARACTERS_EOS.contains(firstChar)) {
                isSentenceStart = true;
            } else if (!isWhitespaceBefore) {
                isSentenceStart = false;
            }
        }
        return text;
    }

    private static String[] parseHeader(String line) {
        return line.split("\t");
    }


    /**
     * Loads the rewrites from a string of tab-separated values.
     */
    private static CommandHolder loadRewrites(String str, CommandMatcher commandMatcher) {
        String[] rows = str.split("\n");
        int length = rows.length;
        if (length == 0) {
            return new CommandHolder(new String[0]);
        }
        CommandHolder commandHolder = new CommandHolder(parseHeader(rows[0]));
        if (length > 1) {
            for (int i = 1; i < length; i++) {
                addLine(commandHolder, rows[i], i, commandMatcher);
            }
        }
        return commandHolder;
    }

    /**
     * Loads the rewrites from a string of tab-separated values.
     * The header is given by a separate argument, the table must not
     * contain the header.
     */
    private static CommandHolder loadRewrites(String str, String[] header, CommandMatcher commandMatcher) {
        CommandHolder commandHolder = new CommandHolder(header);
        String[] rows = str.split("\n");
        if (rows.length > 0) {
            for (int i = 0; i < rows.length; i++) {
                addLine(commandHolder, rows[i], i, commandMatcher);
            }
        }
        return commandHolder;
    }

    /**
     * Loads the rewrites from an URI using a ContentResolver.
     * The first line is a header.
     * Non-header lines are ignored if they start with '#'.
     */
    private static CommandHolder loadRewrites(ContentResolver contentResolver, Uri uri) throws IOException {
        CommandHolder commandHolder = null;
        InputStream inputStream = contentResolver.openInputStream(uri);
        if (inputStream != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line = reader.readLine();
            if (line != null) {
                int lineCounter = 0;
                commandHolder = new CommandHolder(parseHeader(line));
                while ((line = reader.readLine()) != null) {
                    lineCounter++;
                    addLine(commandHolder, line, lineCounter, null);
                }
            }
            inputStream.close();
        }
        if (commandHolder == null) {
            return new CommandHolder(new String[0]);
        }
        return commandHolder;
    }

    private static void addLine(CommandHolder commandHolder, String line, int lineCounter, CommandMatcher commandMatcher) {
        // TODO: removing trailing tabs means that rewrite cannot delete a string
        String[] splits = PATTERN_TRAILING_TABS.matcher(line).replaceAll("").split("\t");
        if (splits.length > 1 && line.charAt(0) != '#') {
            String[] header = commandHolder.getHeader();
            try {
                Command command = getCommand(header, splits, commandMatcher);
                if (command != null) {
                    commandHolder.addCommand(command);
                }
            } catch (PatternSyntaxException e) {
                commandHolder.addError(lineCounter, e.getLocalizedMessage());
            } catch (IllegalArgumentException e) {
                commandHolder.addError(lineCounter, e.getLocalizedMessage());
            }
        }
    }

    /**
     * Creates a command based on the given fields.
     *
     * @param header         header row
     * @param fields         fields of a single row
     * @param commandMatcher command matcher
     * @return command or null if commandMatcher rejects the command
     */
    private static Command getCommand(String[] header, String[] fields, CommandMatcher commandMatcher) {
        String comment = null;
        Pattern locale = null;
        Pattern service = null;
        Pattern app = null;
        Pattern utterance = null;
        String command = null;
        String replacement = null;
        String arg1 = null;
        String arg2 = null;

        for (int i = 0; i < Math.min(header.length, fields.length); i++) {
            String split = fields[i];
            switch (header[i]) {
                case HEADER_COMMENT:
                    comment = split.trim();
                    break;
                case HEADER_LOCALE:
                    locale = Pattern.compile(split.trim());
                    break;
                case HEADER_SERVICE:
                    service = Pattern.compile(split.trim());
                    break;
                case HEADER_APP:
                    app = Pattern.compile(split.trim());
                    break;
                case HEADER_UTTERANCE:
                    split = split.trim();
                    if (split.isEmpty()) {
                        throw new IllegalArgumentException("Empty Utterance");
                    }
                    utterance = Pattern.compile(split, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    break;
                case HEADER_REPLACEMENT:
                    replacement = Command.unescape(split);
                    break;
                case HEADER_COMMAND:
                    command = Command.unescape(split.trim());
                    break;
                case HEADER_ARG1:
                    arg1 = Command.unescape(split);
                    break;
                case HEADER_ARG2:
                    arg2 = Command.unescape(split);
                    break;
                default:
                    // Columns with undefined names are ignored
                    break;
            }
        }

        if (commandMatcher != null && !commandMatcher.matches(locale, service, app)) {
            return null;
        }

        if (arg1 == null) {
            return new Command(comment, locale, service, app, utterance, replacement, command);
        }

        if (arg2 == null) {
            return new Command(comment, locale, service, app, utterance, replacement, command, new String[]{arg1});
        }

        return new Command(comment, locale, service, app, utterance, replacement, command, new String[]{arg1, arg2});
    }
}