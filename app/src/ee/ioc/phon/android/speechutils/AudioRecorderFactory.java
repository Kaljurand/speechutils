package ee.ioc.phon.android.speechutils;

import android.os.Build;

public class AudioRecorderFactory {

    public static AudioRecorder getAudioRecorder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // TODO
            return new EncodedAudioRecorder();
        }
        return new RawAudioRecorder();
    }

    public static AudioRecorder getAudioRecorder(int sampleRate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // TODO
            return new EncodedAudioRecorder(sampleRate);
        }
        return new RawAudioRecorder(sampleRate);
    }
}