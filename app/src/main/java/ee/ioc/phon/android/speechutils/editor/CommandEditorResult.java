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

    public boolean isSuccess() {
        return mSuccess;
    }

    public String ppCommand() {
        return mRewrite.ppCommand();
    }
}