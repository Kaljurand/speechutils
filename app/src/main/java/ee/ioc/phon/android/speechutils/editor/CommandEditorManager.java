package ee.ioc.phon.android.speechutils.editor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * utterance = "go to position 1"
 * pattern = ("go to position (\d+)", "moveAbs", "$1")
 * command = moveAbs($1)
 */
public class CommandEditorManager {

    public static final String MOVE_ABS = "moveAbs";
    public static final String MOVE_REL = "moveRel";
    public static final String SELECT = "select";
    public static final String SELECT_RE_BEFORE = "selectReBefore";
    public static final String SELECT_RE_AFTER = "selectReAfter";
    public static final String REPLACE_SEL_RE = "replaceSelRe";
    public static final String SELECT_ALL = "selectAll";
    public static final String CUT = "cut";
    public static final String COPY = "copy";
    public static final String PASTE = "paste";
    public static final String CUT_ALL = "cutAll";
    public static final String DELETE_ALL = "deleteAll";
    public static final String COPY_ALL = "copyAll";
    public static final String DELETE_LEFT_WORD = "deleteLeftWord";
    public static final String REPLACE = "replace";
    public static final String REPLACE_SEL = "replaceSel";
    public static final String UC_SEL = "ucSel";
    public static final String LC_SEL = "lcSel";
    public static final String INC_SEL = "incSel";
    public static final String SAVE_CLIP = "saveClip";
    public static final String LOAD_CLIP = "loadClip";
    public static final String SHOW_CLIPBOARD = "showClipboard";
    public static final String CLEAR_CLIPBOARD = "clearClipboard";
    public static final String KEY_UP = "keyUp";
    public static final String KEY_DOWN = "keyDown";
    public static final String KEY_LEFT = "keyLeft";
    public static final String KEY_RIGHT = "keyRight";
    public static final String KEY_CODE = "keyCode";
    public static final String KEY_CODE_STR = "keyCodeStr";
    public static final String IME_ACTION_PREVIOUS = "imeActionPrevious";
    public static final String IME_ACTION_NEXT = "imeActionNext";
    public static final String IME_ACTION_DONE = "imeActionDone";
    public static final String IME_ACTION_GO = "imeActionGo";
    public static final String IME_ACTION_SEARCH = "imeActionSearch";
    public static final String IME_ACTION_SEND = "imeActionSend";
    public static final String UNDO = "undo";
    public static final String COMBINE = "combine";
    public static final String APPLY = "apply";
    public static final String ACTIVITY = "activity";
    public static final String GET_URL = "getUrl";

    public static final Map<String, EditorCommand> EDITOR_COMMANDS;

    static {

        Map<String, EditorCommand> aMap = new HashMap<>();

        aMap.put(KEY_UP, (ce, args) -> ce.keyUp());

        aMap.put(KEY_DOWN, (ce, args) -> ce.keyDown());

        aMap.put(KEY_LEFT, (ce, args) -> ce.keyLeft());

        aMap.put(KEY_RIGHT, (ce, args) -> ce.keyRight());

        aMap.put(UNDO, (ce, args) -> {
            int steps = 1;
            if (args != null && args.length > 0) {
                try {
                    steps = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    // Intentional
                }
            }
            return ce.undo(steps);
        });

        aMap.put(COMBINE, (ce, args) -> {
            int numOfOps = 2;
            if (args != null && args.length > 0) {
                try {
                    numOfOps = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    // Intentional
                }
            }
            return ce.combine(numOfOps);
        });

        aMap.put(APPLY, (ce, args) -> {
            int steps = 1;
            if (args != null && args.length > 0) {
                try {
                    steps = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    // Intentional
                }
            }
            return ce.apply(steps);
        });

        aMap.put(MOVE_ABS, (ce, args) -> {
            int pos = 0;
            if (args != null && args.length > 0) {
                try {
                    pos = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    // Intentional
                }
            }
            return ce.moveAbs(pos);
        });

        aMap.put(MOVE_REL, (ce, args) -> {
            int pos = 1;
            if (args != null && args.length > 0) {
                try {
                    pos = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    // Intentional
                }
            }
            return ce.moveRel(pos);
        });

        aMap.put(KEY_CODE, (ce, args) -> {
            if (args != null && args.length > 0) {
                try {
                    return ce.keyCode(Integer.parseInt(args[0]));
                } catch (NumberFormatException e) {
                    // Intentional
                }
            }
            return null;
        });

        aMap.put(KEY_CODE_STR, (ce, args) -> {
            if (args == null || args.length < 1) {
                return null;
            }
            return ce.keyCodeStr(args[0]);
        });

        aMap.put(SELECT, (ce, args) -> {
            if (args == null || args.length < 1) {
                return null;
            }
            return ce.select(args[0]);
        });

        aMap.put(SELECT_RE_BEFORE, (ce, args) -> {
            if (args == null || args.length < 1) {
                return null;
            }
            return ce.selectReBefore(args[0]);
        });

        aMap.put(SELECT_RE_AFTER, (ce, args) -> {
            if (args == null || args.length < 1) {
                return null;
            }
            int n = 1;
            if (args.length > 1) {
                try {
                    n = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    // Intentional
                }
            }
            return ce.selectReAfter(args[0], n);
        });

        aMap.put(REPLACE_SEL_RE, (ce, args) -> {
            if (args == null || args.length < 2) {
                return null;
            }
            return ce.replaceSelRe(args[0], args[1]);
        });

        aMap.put(DELETE_LEFT_WORD, (ce, args) -> ce.deleteLeftWord());

        aMap.put(REPLACE, (ce, args) -> {
            if (args == null || args.length < 1) {
                return null;
            }
            String text1 = args[0];
            String text2 = args.length > 1 ? args[1] : "";
            return ce.replace(text1, text2);
        });

        aMap.put(REPLACE_SEL, (ce, args) -> {
            if (args == null || args.length < 1) {
                return null;
            }
            return ce.replaceSel(args[0]);
        });

        aMap.put(SAVE_CLIP, (ce, args) -> {
            if (args == null || args.length < 2) {
                return null;
            }
            return ce.saveClip(args[0], args[1]);
        });

        aMap.put(LOAD_CLIP, (ce, args) -> {
            if (args == null || args.length < 1) {
                return null;
            }
            return ce.loadClip(args[0]);
        });

        aMap.put(SHOW_CLIPBOARD, (ce, args) -> ce.showClipboard());

        aMap.put(CLEAR_CLIPBOARD, (ce, args) -> ce.clearClipboard());

        aMap.put(UC_SEL, (ce, args) -> ce.ucSel());

        aMap.put(LC_SEL, (ce, args) -> ce.lcSel());

        aMap.put(INC_SEL, (ce, args) -> ce.incSel());

        aMap.put(SELECT_ALL, (ce, args) -> ce.selectAll());

        aMap.put(CUT, (ce, args) -> ce.cut());

        aMap.put(CUT_ALL, (ce, args) -> ce.cutAll());

        aMap.put(DELETE_ALL, (ce, args) -> ce.deleteAll());

        aMap.put(COPY, (ce, args) -> ce.copy());

        aMap.put(COPY_ALL, (ce, args) -> ce.copyAll());

        aMap.put(PASTE, (ce, args) -> ce.paste());

        aMap.put(IME_ACTION_PREVIOUS, (ce, args) -> ce.imeActionPrevious());

        aMap.put(IME_ACTION_NEXT, (ce, args) -> ce.imeActionNext());

        aMap.put(IME_ACTION_DONE, (ce, args) -> ce.imeActionDone());

        aMap.put(IME_ACTION_GO, (ce, args) -> ce.imeActionGo());

        aMap.put(IME_ACTION_SEARCH, (ce, args) -> ce.imeActionSearch());

        aMap.put(IME_ACTION_SEND, (ce, args) -> ce.imeActionSend());

        aMap.put(ACTIVITY, (ce, args) -> {
            if (args == null || args.length < 1) {
                return null;
            }
            return ce.activity(args[0]);
        });

        aMap.put(GET_URL, (ce, args) -> {
            if (args == null || args.length < 1) {
                return null;
            }
            return ce.getUrl(args[0]);
        });

        EDITOR_COMMANDS = Collections.unmodifiableMap(aMap);
    }

    public interface EditorCommand {
        Op getOp(CommandEditor commandEditor, String[] args);
    }

    public static EditorCommand get(String id) {
        return EDITOR_COMMANDS.get(id);
    }
}