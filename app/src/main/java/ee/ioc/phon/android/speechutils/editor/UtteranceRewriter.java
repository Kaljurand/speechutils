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
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class UtteranceRewriter {

    private static final Pattern PATTERN_TRAILING_TABS = Pattern.compile("\t*$");

    public static String[] HEADER = {"Comment", "Locale", "Service", "App", "Utterance", "Replacement", "Command", "Arg1", "Arg2"};

    public static class Rewrite {
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

    private final List<Command> mCommands;

    public UtteranceRewriter(List<Command> commands) {
        assert commands != null;
        mCommands = commands;
    }

    public UtteranceRewriter(String str) {
        this(loadRewrites(str));
    }

    public UtteranceRewriter(ContentResolver contentResolver, Uri uri) throws IOException {
        this(loadRewrites(contentResolver, uri));
    }

    public int size() {
        return mCommands.size();
    }

    /**
     * Rewrites and returns the given string,
     * and the first matching command.
     */
    public Rewrite getRewrite(String str) {
        for (Command command : mCommands) {
            Pair<String, String[]> pair = command.match(str);
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

    /**
     * Serializes the rewrites as tab-separated-values.
     */
    public String toTsv() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(TextUtils.join("\t", HEADER));
        for (Command command : mCommands) {
            stringBuilder.append(command.toTsv());
            stringBuilder.append('\n');
        }
        return stringBuilder.toString();
    }

    public String[] toStringArray() {
        String[] array = new String[mCommands.size()];
        int i = 0;
        for (Command command : mCommands) {
            array[i++] = command.toPp();
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


    /**
     * Loads the rewrites from tab-separated values.
     */
    private static List<Command> loadRewrites(String str) {
        assert str != null;
        List<Command> commands = new ArrayList<>();
        String[] rows = str.split("\n");
        if (rows.length > 1) {
            String[] header = rows[0].split("\t");
            for (int i = 1; i < rows.length; i++) {
                if (!addLine(commands, header, rows[i])) {
                    break;
                }
            }
        }
        return commands;
    }


    /**
     * Loads the rewrites from an URI using a ContentResolver.
     * The first line is a header.
     * Non-header lines are ignored if they start with '#'.
     */
    private static List<Command> loadRewrites(ContentResolver contentResolver, Uri uri) throws IOException {
        InputStream inputStream = contentResolver.openInputStream(uri);
        List<Command> commands = new ArrayList<>();
        if (inputStream != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line = reader.readLine();
            if (line != null) {
                String[] header = line.split("\t");
                while ((line = reader.readLine()) != null) {
                    if (line.charAt(0) == '#') {
                        continue;
                    }
                    if (!addLine(commands, header, line)) {
                        break;
                    }

                }
            }
            inputStream.close();
        }
        return commands;
    }

    private static boolean addLine(List<Command> commands, String[] header, String line) {
        // TODO: removing trailing tabs means that rewrite cannot delete a string
        String[] splits = PATTERN_TRAILING_TABS.matcher(line).replaceAll("").split("\t");
        if (splits.length > 1) {
            try {
                commands.add(getCommand(header, splits));
                return true;
            } catch (PatternSyntaxException e) {
                // TODO: collect and expose buggy entries
                return false;
            } catch (IllegalArgumentException e) {
                // Unsupported header field
                return false;
            }
        }
        return true;
    }

    private static Command getCommand(String[] header, String[] splits) {
        String comment = null;
        Locale locale = null;
        Pattern service = null;
        Pattern app = null;
        Pattern utterance = null;
        String command = null;
        String replacement = null;
        String arg1 = null;
        String arg2 = null;

        for (int i = 0; i < Math.min(header.length, splits.length); i++) {
            String split = splits[i];
            switch (header[i]) {
                case "Comment":
                    comment = split;
                    break;
                case "Locale":
                    locale = new Locale(split);
                    break;
                case "Service":
                    service = Pattern.compile(split);
                    break;
                case "App":
                    app = Pattern.compile(split);
                    break;
                case "Utterance":
                    utterance = Pattern.compile(split, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    break;
                case "Replacement":
                    replacement = Command.unescape(split);
                    break;
                case "Command":
                    command = Command.unescape(split);
                    break;
                case "Arg1":
                    arg1 = Command.unescape(split);
                    break;
                case "Arg2":
                    arg2 = Command.unescape(split);
                    break;
                default:
                    throw new IllegalArgumentException(header[i]);
            }
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