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
import java.util.regex.PatternSyntaxException;

import ee.ioc.phon.android.speechutils.Log;

public class UtteranceRewriter {

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
     * and the last matching command.
     * TODO: improve this
     */
    public Triple rewrite(String str) {
        String commandId = null;
        String[] args = null;
        for (Command command : mCommands) {
            Log.i("editor: rewrite with command: " + str + ": " + command);
            Pair<String, String[]> pair = command.match(str);
            if (pair != null) {
                str = pair.first;
                commandId = command.getId();
                args = pair.second;
                Log.i("editor: rewrite: success: " + str + ": " + commandId + "(" + TextUtils.join(", ", args) + ")");
            }
        }
        return new Triple(commandId, str, args);
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
            stringBuilder.append(escape(command.mPattern.toString()));
            stringBuilder.append('\t');
            stringBuilder.append(escape(command.mReplacement));
            if (command.mId != null) {
                stringBuilder.append('\t');
                stringBuilder.append(escape(command.mId));
            }
            if (command.mArgs != null) {
                for (String arg : command.mArgs) {
                    stringBuilder.append('\t');
                    stringBuilder.append(escape(arg));
                }
            }
            stringBuilder.append('\n');
        }
        return stringBuilder.toString();
    }

    public String[] toStringArray() {
        String[] array = new String[mCommands.size()];
        int i = 0;
        for (Command command : mCommands) {
            array[i] = pp(command.mPattern.toString())
                    + '\n'
                    + pp(command.mReplacement);
            if (command.mId != null) {
                array[i] += '\n' + command.mId;
            }
            if (command.mArgs != null) {
                for (String arg : command.mArgs) {
                    array[i] += '\n' + arg;
                }
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
        String[] splits = line.split("\t");
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
        List<String> args = new ArrayList<>();
        for (int i = 3; i < splits.length; i++) {
            args.add(unescape(splits[i]));
        }
        String commandId = null;
        if (splits.length > 2) {
            commandId = unescape(splits[2]);
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