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

import ee.ioc.phon.android.speechutils.Log;

public class UtteranceRewriter {

    private static final Pattern PATTERN_TRAILING_TABS = Pattern.compile("\t*$");

    static class Triple {
        final String mId;
        final String mStr;
        final String[] mArgs;

        public Triple(String id, String str, String[] args) {
            mId = id;
            mStr = str;
            mArgs = args;
        }
    }

    private List<Command> mCommands;

    public UtteranceRewriter() {
        mCommands = new ArrayList<>();
    }

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
    public Triple rewrite(String str) {
        for (Command command : mCommands) {
            Log.i("editor: rewrite with command: " + str + ": " + command);
            Pair<String, String[]> pair = command.match(str);
            if (pair != null) {
                str = pair.first;
                String commandId = command.getId();
                if (commandId != null) {
                    String[] args = pair.second;
                    Log.i("editor: rewrite: success: " + str + ": " + commandId + "(" + TextUtils.join(",", args) + ")");
                    return new Triple(commandId, str, args);
                }
            }
        }
        return new Triple(null, str, null);
    }

    /**
     * Rewrites and returns the given results.
     * TODO: improve this
     */
    public Pair<Pair<String, String[]>, List<String>> rewrite(List<String> results) {
        String commandId = null;
        String[] args = null;
        List<String> rewrittenResults = new ArrayList<>();
        for (String result : results) {
            Triple triple = rewrite(result);
            rewrittenResults.add(triple.mStr);
            commandId = triple.mId;
            args = triple.mArgs;
        }
        return new Pair<>(new Pair<>(commandId, args), rewrittenResults);
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