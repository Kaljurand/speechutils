package ee.ioc.phon.android.speechutils.editor;

public abstract class Op {
    public static final Op NO_OP = new Op("NO_OP") {

        @Override
        public Op run() {
            return null;
        }
    };

    private final String mId;

    public Op(String id) {
        mId = id;
    }

    public String toString() {
        return mId;
    }

    public boolean isNoOp() {
        return "NO_OP".equals(mId);
    }

    public abstract Op run();
}