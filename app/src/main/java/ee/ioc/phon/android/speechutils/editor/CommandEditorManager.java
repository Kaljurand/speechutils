package ee.ioc.phon.android.speechutils.editor;

import android.text.TextUtils;

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
        boolean execute(String[] args);
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
        if (editorCommand == null) {
            return false;
        }
        if (args == null) {
            Log.i(commandId + "(null)");
        } else {
            Log.i(commandId + "(" + TextUtils.join(",", args) + ")");
        }
        return editorCommand.execute(args);
    }

    public boolean execute(String commandId, String[] args, String textRewritten) {
        mCommandEditor.commitText(textRewritten);
        if (commandId != null) {
            return execute(commandId, args);
        }
        return false;
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

        mEditorCommands.put("goToEnd", new EditorCommand() {

            @Override
            public boolean execute(String... args) {
                return mCommandEditor.goToEnd();
            }
        });

        mEditorCommands.put("goToCharacterPosition", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                int pos = 0;
                if (args != null && args.length > 0) {
                    try {
                        pos = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e) {
                    }
                }
                return mCommandEditor.goToCharacterPosition(pos);
            }
        });

        mEditorCommands.put("select", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                if (args == null || args.length != 1) {
                    return false;
                }
                return mCommandEditor.select(args[0]);
            }
        });

        mEditorCommands.put("delete", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                if (args == null || args.length != 1) {
                    return false;
                }
                return mCommandEditor.delete(args[0]);
            }
        });

        mEditorCommands.put("replace", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                if (args == null || args.length != 2) {
                    return false;
                }
                return mCommandEditor.replace(args[0], args[1]);
            }
        });

        mEditorCommands.put("addSpace", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                return mCommandEditor.addSpace();
            }
        });

        mEditorCommands.put("addNewline", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                return mCommandEditor.addNewline();
            }
        });

        mEditorCommands.put("selectAll", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                return mCommandEditor.selectAll();
            }
        });

        mEditorCommands.put("cut", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                return mCommandEditor.cut();
            }
        });

        mEditorCommands.put("copy", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                return mCommandEditor.copy();
            }
        });

        mEditorCommands.put("copyAll", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                return mCommandEditor.copyAll();
            }
        });

        mEditorCommands.put("paste", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                return mCommandEditor.paste();
            }
        });

        mEditorCommands.put("go", new EditorCommand() {

            @Override
            public boolean execute(String[] args) {
                return mCommandEditor.go();
            }
        });
    }

}