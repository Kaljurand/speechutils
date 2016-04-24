package ee.ioc.phon.android.speechutils.editor;

import java.util.HashMap;
import java.util.Map;

import ee.ioc.phon.android.speechutils.Log;

/**
 * utterance = "go to position 1"
 * pattern = ("goTo", "go to position (\d+)")
 * command = goToCharacterPosition(group(1).toInt())
 * <p/>
 * TODO: add a way to specify the order and content of the arguments
 */
public class CommandEditorManager {

    public interface EditorCommand {
        boolean execute(String... args);
    }

    private final CommandEditor mCommandEditor;
    private final Map<String, EditorCommand> mEditorCommands = new HashMap<>();

    public CommandEditorManager(CommandEditor commandEditor) {
        mCommandEditor = commandEditor;
        init();
    }


    public EditorCommand get(String id) {
        return mEditorCommands.get(id);
    }

    public boolean execute(String id, String... args) {
        EditorCommand command = get(id);
        Log.i("editor: executing: " + id + "( " + args + " ): " + command);
        if (command == null) {
            return false;
        }
        return command.execute(args);
    }

    public boolean execute(Command command) {
        EditorCommand editorCommand = get(command.getId());
        Log.i("editor: executing: " + command);
        if (editorCommand == null) {
            return false;
        }
        // TODO: add the arguments
        return editorCommand.execute();
    }

    /*
    public boolean executeUtterance(Command command, String utterance) {
        Log.i("editor: matching utterance: " + utterance);
        Matcher m = command.matcher(utterance);
        if (m.matches()) {
            int groupCount = m.groupCount();
            Log.i("editor: match found: " + command.getPattern() + ": groups = " + groupCount);
            if (groupCount == 1) {
                return execute(command.getId(), m.group(1));
            } else if (groupCount == 2) {
                return execute(command.getId(), m.group(1), m.group(2));
            }
            return execute(command.getId());
        }
        return false;
    }
    */

    private void init() {

        mEditorCommands.put("goToPreviousField", new EditorCommand() {

            @Override
            public boolean execute(String... args) {
                return mCommandEditor.goToPreviousField();
            }
        });

        mEditorCommands.put("goToNextField", new EditorCommand() {

            @Override
            public boolean execute(String... args) {
                return mCommandEditor.goToNextField();
            }
        });

        mEditorCommands.put("goToCharacterPosition", new EditorCommand() {

            @Override
            public boolean execute(String... args) {
                int pos = 0;
                if (args.length > 0) {
                    try {
                        pos = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e) {
                    }
                }
                return mCommandEditor.goToCharacterPosition(pos);
            }
        });

        mEditorCommands.put("selectAll", new EditorCommand() {

            @Override
            public boolean execute(String... args) {
                return mCommandEditor.selectAll();
            }
        });
    }

}