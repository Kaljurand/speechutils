package ee.ioc.phon.android.speechutils.editor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * utterance = "go to position 1"
 * pattern = ("go to position (\d+)", "goToCharacterPosition", "$1")
 * command = goToCharacterPosition($1)
 */
public class CommandEditorManager {

    public interface EditorCommand {
        Op getOp(CommandEditor commandEditor, String[] args);
    }

    public static final String GO_UP = "goUp";
    public static final String GO_DOWN = "goDown";
    public static final String GO_LEFT = "goLeft";
    public static final String GO_RIGHT = "goRight";
    public static final String GO_TO_PREVIOUS_FIELD = "goToPreviousField";
    public static final String GO_TO_NEXT_FIELD = "goToNextField";
    public static final String GO_TO_CHARACTER_POSITION = "goToCharacterPosition";
    public static final String GO_FORWARD = "goForward";
    public static final String GO_BACKWARD = "goBackward";
    public static final String GO_TO_END = "goToEnd";
    public static final String SELECT = "select";
    public static final String SELECT_RE_BEFORE = "selectReBefore";
    public static final String SELECT_RE_AFTER = "selectReAfter";
    public static final String REPLACE_SEL_RE = "replaceSelRe";
    public static final String RESET_SEL = "resetSel";
    public static final String SELECT_ALL = "selectAll";
    public static final String CUT = "cut";
    public static final String COPY = "copy";
    public static final String PASTE = "paste";
    public static final String CUT_ALL = "cutAll";
    public static final String DELETE_ALL = "deleteAll";
    public static final String COPY_ALL = "copyAll";
    public static final String DELETE_LEFT_WORD = "deleteLeftWord";
    public static final String DELETE = "delete";
    public static final String REPLACE = "replace";
    public static final String REPLACE_SEL = "replaceSel";
    public static final String UC_SEL = "ucSel";
    public static final String LC_SEL = "lcSel";
    public static final String INC_SEL = "incSel";
    public static final String SAVE_SEL = "saveSel";
    public static final String LOAD_SEL = "loadSel";
    public static final String KEY_CODE = "keyCode";
    public static final String KEY_CODE_STR = "keyCodeStr";
    public static final String IME_ACTION_DONE = "imeActionDone";
    public static final String IME_ACTION_GO = "imeActionGo";
    public static final String IME_ACTION_SEARCH = "imeActionSearch";
    public static final String IME_ACTION_SEND = "imeActionSend";
    public static final String UNDO = "undo";
    public static final String COMBINE = "combine";
    public static final String APPLY = "apply";

    public static final Map<String, EditorCommand> EDITOR_COMMANDS;

    static {

        Map<String, EditorCommand> aMap = new HashMap<>();

        aMap.put(GO_UP, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.goUp();
            }
        });
        aMap.put(GO_DOWN, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.goDown();
            }
        });
        aMap.put(GO_LEFT, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.goLeft();
            }
        });
        aMap.put(GO_RIGHT, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.goRight();
            }
        });

        aMap.put(UNDO, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                int steps = 1;
                if (args != null && args.length > 0) {
                    try {
                        steps = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e) {
                        // Intentional
                    }
                }
                return ce.undo(steps);
            }
        });

        aMap.put(COMBINE, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                int numOfOps = 2;
                if (args != null && args.length > 0) {
                    try {
                        numOfOps = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e) {
                        // Intentional
                    }
                }
                return ce.combine(numOfOps);
            }
        });

        aMap.put(APPLY, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                int steps = 1;
                if (args != null && args.length > 0) {
                    try {
                        steps = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e) {
                        // Intentional
                    }
                }
                return ce.apply(steps);
            }
        });

        aMap.put(GO_TO_PREVIOUS_FIELD, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.goToPreviousField();
            }
        });

        aMap.put(GO_TO_NEXT_FIELD, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.goToNextField();
            }
        });

        aMap.put(GO_TO_END, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.goToEnd();
            }
        });

        aMap.put(GO_TO_CHARACTER_POSITION, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
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

        aMap.put(GO_FORWARD, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
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

        aMap.put(GO_BACKWARD, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
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

        aMap.put(KEY_CODE, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                if (args != null && args.length > 0) {
                    try {
                        return ce.keyCode(Integer.parseInt(args[0]));
                    } catch (NumberFormatException e) {
                        // Intentional
                    }
                }
                return null;
            }
        });

        aMap.put(KEY_CODE_STR, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                if (args == null || args.length != 1) {
                    return null;
                }
                return ce.keyCodeStr(args[0]);
            }
        });

        aMap.put(SELECT, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                if (args == null || args.length != 1) {
                    return null;
                }
                return ce.select(args[0]);
            }
        });

        aMap.put(SELECT_RE_BEFORE, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                if (args == null || args.length != 1) {
                    return null;
                }
                return ce.selectReBefore(args[0]);
            }
        });

        aMap.put(SELECT_RE_AFTER, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                if (args == null || args.length == 0 || args.length > 2) {
                    return null;
                }
                int n = 1;
                if (args.length == 2) {
                    try {
                        n = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        // Intentional
                    }
                }
                return ce.selectReAfter(args[0], n);
            }
        });

        aMap.put(REPLACE_SEL_RE, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                if (args == null || args.length != 2) {
                    return null;
                }
                return ce.replaceSelRe(args[0], args[1]);
            }
        });

        aMap.put(DELETE_LEFT_WORD, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.deleteLeftWord();
            }
        });

        aMap.put(DELETE, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                if (args == null || args.length != 1) {
                    return null;
                }
                return ce.delete(args[0]);
            }
        });

        aMap.put(REPLACE, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                if (args == null || args.length != 2) {
                    return null;
                }
                return ce.replace(args[0], args[1]);
            }
        });

        aMap.put(REPLACE_SEL, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                if (args == null || args.length != 1) {
                    return null;
                }
                return ce.replaceSel(args[0]);
            }
        });

        aMap.put(SAVE_SEL, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                if (args == null || args.length != 1) {
                    return null;
                }
                return ce.saveSel(args[0]);
            }
        });

        aMap.put(LOAD_SEL, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                if (args == null || args.length != 1) {
                    return null;
                }
                return ce.loadSel(args[0]);
            }
        });

        aMap.put(RESET_SEL, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.resetSel();
            }
        });

        aMap.put(UC_SEL, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.ucSel();
            }
        });

        aMap.put(LC_SEL, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.lcSel();
            }
        });

        aMap.put(INC_SEL, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.incSel();
            }
        });

        aMap.put(SELECT_ALL, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.selectAll();
            }
        });

        aMap.put(CUT, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.cut();
            }
        });

        aMap.put(CUT_ALL, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.cutAll();
            }
        });

        aMap.put(DELETE_ALL, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.deleteAll();
            }
        });

        aMap.put(COPY, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.copy();
            }
        });

        aMap.put(COPY_ALL, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.copyAll();
            }
        });

        aMap.put(PASTE, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.paste();
            }
        });

        aMap.put(IME_ACTION_DONE, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.imeActionDone();
            }
        });

        aMap.put(IME_ACTION_GO, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.imeActionGo();
            }
        });

        aMap.put(IME_ACTION_SEARCH, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.imeActionSearch();
            }
        });

        aMap.put(IME_ACTION_SEND, new EditorCommand() {

            @Override
            public Op getOp(CommandEditor ce, String[] args) {
                return ce.imeActionSend();
            }
        });

        EDITOR_COMMANDS = Collections.unmodifiableMap(aMap);
    }

    public static EditorCommand get(String id) {
        return EDITOR_COMMANDS.get(id);
    }
}