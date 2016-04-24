package ee.ioc.phon.android.speechutils.editor;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

import ee.ioc.phon.android.speechutils.Log;

public class UtteranceRewriter {

    private final Commands mCommands;

    public UtteranceRewriter() {
        mCommands = new Commands();
    }

    public UtteranceRewriter(Commands commands) {
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
    public Pair<Command, String> rewrite(String str) {
        Command c = null;
        for (Command command : mCommands.asList()) {
            Matcher m = command.mPattern.matcher(str);
            if (m.matches()) {
                str = m.replaceAll(command.mReplacement);
                c = command;
                // TODO: resolve the arguments
            }
        }
        return new Pair(c, str);
    }

    /**
     * Rewrites and returns the given results.
     * TODO: improve this
     */
    public Pair<Command, List<String>> rewrite(List<String> results) {
        Command command = null;
        List<String> rewrittenResults = new ArrayList<>();
        for (String result : results) {
            Pair<Command, String> pair = rewrite(result);
            rewrittenResults.add(pair.second);
            command = pair.first;
        }
        Log.i("editor: " + command);
        return new Pair(command, rewrittenResults);
    }

    /**
     * Serializes the rewrites as tab-separated-values.
     */
    public String toTsv() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Command command : mCommands.asList()) {
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
        for (Command command : mCommands.asList()) {
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
    private static Commands loadRewrites(String str) {
        assert str != null;
        Commands commands = new Commands();
        for (String line : str.split("\n")) {
            addLine(commands, line);
        }
        return commands;
    }


    /**
     * Loads the rewrites from an URI using a ContentResolver.
     */
    private static Commands loadRewrites(ContentResolver contentResolver, Uri uri) throws IOException {
        InputStream inputStream = contentResolver.openInputStream(uri);
        Commands commands = new Commands();
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

    private static void addLine(Commands commands, String line) {
        String[] splits = line.split("\t");
        try {
            // TODO: add support for arguments
            if (splits.length >= 3) {
                commands.add(splits[0], "", splits[2]);
            } else if (splits.length >= 2) {
                commands.add(splits[0], splits[1], null);
            }
        } catch (PatternSyntaxException e) {
            // TODO: collect and expose buggy entries
        }
    }

    /**
     * Maps newlines and tabs to literals of the form "\n" and "\t".
     */
    private static String escape(String str) {
        return str.replace("\n", "\\n").replace("\t", "\\t");
    }

    private static String pp(String str) {
        return escape(str).replace(" ", "·");
    }


    static class Commands {

        private final List<Command> mCommands;

        public Commands() {
            mCommands = new ArrayList<>();
        }

        public void add(String pattern, String replacement, String commandId) {
            mCommands.add(new Command(pattern, replacement, commandId, null));
        }

        public void add(String pattern, String replacement, String commandId, List<String> args) {
            mCommands.add(new Command(pattern, replacement, commandId, args));
        }

        public int size() {
            return mCommands.size();
        }

        public List<Command> asList() {
            return mCommands;
        }
    }
}