package ee.ioc.phon.android.speechutils.editor;

public class CommandEditorResult {

    private final boolean mSuccess;
    private final UtteranceRewriter.Rewrite mRewrite;

    public CommandEditorResult(boolean success, UtteranceRewriter.Rewrite rewrite) {
        mSuccess = success;
        mRewrite = rewrite;
    }

    public boolean isCommand() {
        return mRewrite.isCommand();
    }

    public String toString() {
        if (mSuccess) {
            return "+" + mRewrite.toString();
        }
        return "-" + mRewrite.toString();
    }
}