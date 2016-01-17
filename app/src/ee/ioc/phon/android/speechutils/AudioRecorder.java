package ee.ioc.phon.android.speechutils;

public interface AudioRecorder {
    String getWsArgs();

    State getState();

    byte[] consumeRecordingAndTruncate();

    byte[] consumeRecording();

    void start();

    float getRmsdb();

    void release();

    boolean isPausing();

    enum State {
        // recorder is ready, but not yet recording
        READY,

        // recorder recording
        RECORDING,

        // error occurred, reconstruction needed
        ERROR,

        // recorder stopped
        STOPPED
    }
}