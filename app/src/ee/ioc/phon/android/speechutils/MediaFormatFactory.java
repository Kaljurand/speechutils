package ee.ioc.phon.android.speechutils;

import android.annotation.TargetApi;
import android.media.MediaFormat;
import android.os.Build;

public class MediaFormatFactory {

    // TODO: add mimes
    public enum Type {
        AAC, AMR, FLAC
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static MediaFormat createMediaFormat(Type type, int sampleRate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            MediaFormat format = new MediaFormat();
            // TODO: this causes a crash in MediaCodec.configure
            //format.setString(MediaFormat.KEY_FRAME_RATE, null);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            if (type == Type.AAC) {
                format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, 2); // TODO: or 39?
                format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
            } else if (type == Type.FLAC) {
                //format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_FLAC); // API=21
                format.setString(MediaFormat.KEY_MIME, "audio/flac");
                format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
                //TODO: use another bit rate, does not seem to have effect always
                //format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            } else {
                format.setString(MediaFormat.KEY_MIME, "audio/amr-wb");
                format.setInteger(MediaFormat.KEY_BIT_RATE, 23050);
            }
            return format;
        }
        return null;
    }

    //final int kAACProfiles[] = {
    //        2 /* OMX_AUDIO_AACObjectLC */,
    //        5 /* OMX_AUDIO_AACObjectHE */,
    //        39 /* OMX_AUDIO_AACObjectELD */
    //};

    //if (kAACProfiles[k] == 5 && kSampleRates[i] < 22050) {
    //    // Is this right? HE does not support sample rates < 22050Hz?
    //    continue;
    //}
    // final int kSampleRates[] = {8000, 11025, 22050, 44100, 48000};
    // final int kBitRates[] = {64000, 128000};
}