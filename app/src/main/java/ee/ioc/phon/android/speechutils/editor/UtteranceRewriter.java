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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class UtteranceRewriter {

    private static final Pattern PATTERN_TRAILING_TABS = Pattern.compile("\t*$");

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
        for (Command command : mCommands) {
            stringBuilder.append(escape(command.getPattern().toString()));
            stringBuilder.append('\t');
            stringBuilder.append(escape(command.getReplacement()));
            if (command.getId() != null) {
                stringBuilder.append('\t');
                stringBuilder.append(escape(command.getId()));
            }
            for (String arg : command.getArgs()) {
                stringBuilder.append('\t');
                stringBuilder.append(escape(arg));
            }
            stringBuilder.append('\n');
        }
        return stringBuilder.toString();
    }

    public String[] toStringArray() {
        String[] array = new String[mCommands.size()];
        int i = 0;
        for (Command command : mCommands) {
            array[i] = pp(command.getPattern().toString())
                    + '\n'
                    + pp(command.getReplacement());
            if (command.getId() != null) {
                array[i] += '\n' + command.getId();
            }
            for (String arg : command.getArgs()) {
                array[i] += '\n' + arg;
            }
            i++;
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
        for (String line : str.split("\n")) {
            addLine(commands, line);
        }
        return commands;
    }


    /**
     * Loads the rewrites from an URI using a ContentResolver.
     */
    private static List<Command> loadRewrites(ContentResolver contentResolver, Uri uri) throws IOException {
        InputStream inputStream = contentResolver.openInputStream(uri);
        List<Command> commands = new ArrayList<>();
        if (inputStream != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                addLine(commands, line);
            }
            inputStream.close();
        }
        return commands;
    }

    private static boolean addLine(List<Command> commands, String line) {
        // TODO: removing trailing tabs means that rewrite cannot delete a string
        String[] splits = PATTERN_TRAILING_TABS.matcher(line).replaceAll("").split("\t");
        if (splits.length > 1) {
            try {
                commands.add(getCommand(splits));
                return true;
            } catch (PatternSyntaxException e) {
                // TODO: collect and expose buggy entries
            }
        }
        return false;
    }

    private static Command getCommand(String[] splits) {
        String commandId = null;
        String[] args = null;
        int numOfArgs = splits.length - 3;

        if (numOfArgs >= 0) {
            commandId = unescape(splits[2]);
        }

        if (numOfArgs > 0) {
            args = new String[numOfArgs];
            for (int i = 0; i < numOfArgs; i++) {
                args[i] = unescape(splits[i + 3]);
            }
        }

        return new Command(unescape(splits[0]), unescape(splits[1]), commandId, args);
    }

    /**
     * Maps newlines and tabs to literals of the form "\n" and "\t".
     */
    private static String escape(String str) {
        return str.replace("\n", "\\n").replace("\t", "\\t");
    }

    /**
     * Maps literals of the form "\n" and "\t" to newlines and tabs.
     */
    private static String unescape(String str) {
        return str.replace("\\n", "\n").replace("\\t", "\t");
    }

    private static String pp(String str) {
        return escape(str).replace(" ", "·");
    }
}