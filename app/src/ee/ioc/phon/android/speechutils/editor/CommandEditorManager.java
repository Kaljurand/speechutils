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

    public boolean execute(String commandId, String[] args) {
        EditorCommand editorCommand = get(commandId);
        Log.i("editor: executing: " + commandId);
        if (editorCommand == null) {
            return false;
        }
        int len = args.length;
        if (len == 3) {
            return editorCommand.execute(args[0], args[1], args[2]);
        } else if (len == 2) {
            return editorCommand.execute(args[0], args[1]);
        } else if (len == 1) {
            return editorCommand.execute(args[0]);
        }
        return editorCommand.execute();
    }

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