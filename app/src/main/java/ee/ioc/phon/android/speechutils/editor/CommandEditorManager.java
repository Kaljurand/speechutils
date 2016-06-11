package ee.ioc.phon.android.speechutils.editor;

import android.text.TextUtils;

import java.util.Collections;
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
        boolean execute(CommandEditor commandEditor, String[] args);
    }

    private final CommandEditor mCommandEditor;

    public static final String GO_UP = "goUp";
    public static final String GO_DOWN = "goDown";
    public static final String GO_LEFT = "goLeft";
    public static final String GO_RIGHT = "goRight";
    public static final String GO_TO_PREVIOUS_FIELD = "goToPreviousField";
    public static final String GO_TO_NEXT_FIELD = "goToNextField";
    // TODO ...
    public static final String UNDO = "undo";

    public static final Map<String, EditorCommand> EDITOR_COMMANDS;

    static {

        Map<String, EditorCommand> aMap = new HashMap<>();

        aMap.put(GO_UP, new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.goUp();
            }
        });
        aMap.put(GO_DOWN, new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.goDown();
            }
        });
        aMap.put(GO_LEFT, new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.goLeft();
            }
        });
        aMap.put(GO_RIGHT, new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.goRight();
            }
        });
        aMap.put(UNDO, new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.undo();
            }
        });

        aMap.put(GO_TO_PREVIOUS_FIELD, new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.goToPreviousField();
            }
        });

        aMap.put(GO_TO_NEXT_FIELD, new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.goToNextField();
            }
        });

        aMap.put("goToEnd", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.goToEnd();
            }
        });

        aMap.put("goToCharacterPosition", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                int pos = 0;
                if (args != null && args.length > 0) {
                    try {
                        pos = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e) {
                        // Intentional
                    }
                }
                return ce.goToCharacterPosition(pos);
            }
        });

        aMap.put("goForward", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                int pos = 1;
                if (args != null && args.length > 0) {
                    try {
                        pos = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e) {
                        // Intentional
                    }
                }
                return ce.goForward(pos);
            }
        });

        aMap.put("goBackward", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                int pos = 1;
                if (args != null && args.length > 0) {
                    try {
                        pos = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e) {
                        // Intentional
                    }
                }
                return ce.goBackward(pos);
            }
        });

        aMap.put("select", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                if (args == null || args.length != 1) {
                    return false;
                }
                return ce.select(args[0]);
            }
        });

        aMap.put("delete", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                if (args == null || args.length != 1) {
                    return false;
                }
                return ce.delete(args[0]);
            }
        });

        aMap.put("replace", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                if (args == null || args.length != 2) {
                    return false;
                }
                return ce.replace(args[0], args[1]);
            }
        });

        aMap.put("replaceSel", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                if (args == null || args.length != 1) {
                    return false;
                }
                return ce.replaceSel(args[0]);
            }
        });

        aMap.put("resetSel", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.ucSel();
            }
        });

        aMap.put("ucSel", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.ucSel();
            }
        });

        aMap.put("lcSel", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.lcSel();
            }
        });

        aMap.put("incSel", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.incSel();
            }
        });

        aMap.put("addSpace", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.addSpace();
            }
        });

        aMap.put("addNewline", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.addNewline();
            }
        });

        aMap.put("selectAll", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.selectAll();
            }
        });

        aMap.put("cut", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.cut();
            }
        });

        aMap.put("cutAll", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.cutAll();
            }
        });

        aMap.put("deleteAll", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.deleteAll();
            }
        });

        aMap.put("copy", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.copy();
            }
        });

        aMap.put("copyAll", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.copyAll();
            }
        });

        aMap.put("paste", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.paste();
            }
        });

        aMap.put("go", new EditorCommand() {

            @Override
            public boolean execute(CommandEditor ce, String[] args) {
                return ce.go();
            }
        });

        EDITOR_COMMANDS = Collections.unmodifiableMap(aMap);
    }

    public CommandEditorManager(CommandEditor commandEditor) {
        mCommandEditor = commandEditor;
    }

    public EditorCommand get(String id) {
        return EDITOR_COMMANDS.get(id);
    }

    public boolean execute(String commandId, String[] args, String textRewritten) {
        mCommandEditor.commitText(textRewritten, false);
        return commandId != null && execute(commandId, args);
    }

    private boolean execute(String commandId, String[] args) {
        EditorCommand editorCommand = get(commandId);
        if (editorCommand == null) {
            return false;
        }
        if (args == null) {
            Log.i(commandId + "(null)");
        } else {
            Log.i(commandId + "(" + TextUtils.join(",", args) + ")");
        }
        return editorCommand.execute(mCommandEditor, args);
    }
}