package ee.ioc.phon.android.speechutils.utils;

import android.annotation.TargetApi;
import android.media.MediaFormat;
import android.os.Build;

import java.util.List;

import ee.ioc.phon.android.speechutils.Log;

public class AudioUtils {

    public static byte[] getRecordingAsWav(byte[] pcm, int sampleRate, short resolutionInBytes, short channels) {
        int headerLen = 44;
        int byteRate = sampleRate * resolutionInBytes; // sampleRate*(16/8)*1 ???
        int totalAudioLen = pcm.length;
        int totalDataLen = totalAudioLen + headerLen;

        byte[] header = new byte[headerLen];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = (byte) (8 * resolutionInBytes);  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        byte[] wav = new byte[header.length + pcm.length];
        System.arraycopy(header, 0, wav, 0, header.length);
        System.arraycopy(pcm, 0, wav, header.length, pcm.length);
        return wav;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void showMetrics(MediaFormat format, int numBytesSubmitted, int numBytesDequeued) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Log.i("queued a total of " + numBytesSubmitted + " bytes, " + "dequeued " + numBytesDequeued + " bytes.");
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int inBitrate = sampleRate * channelCount * 16;  // bit/sec
            int outBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
            float desiredRatio = (float) outBitrate / (float) inBitrate;
            float actualRatio = (float) numBytesDequeued / (float) numBytesSubmitted;
            Log.i("desiredRatio = " + desiredRatio + ", actualRatio = " + actualRatio);
        }
    }

    public static byte[] concatenateBuffers(List<byte[]> buffers) {
        byte[] buffersConcatenated;
        int sum = 0;
        for (byte[] ba : buffers) {
            sum = sum + ba.length;
        }
        buffersConcatenated = new byte[sum];
        int pos = 0;
        for (byte[] ba : buffers) {
            System.arraycopy(ba, 0, buffersConcatenated, pos, ba.length);
            pos = pos + ba.length;
        }
        return buffersConcatenated;
    }

    /**
     * Just for testing...
     */
    public static void showSomeBytes(String tag, byte[] bytes) {
        Log.i("enc: " + tag + ": length: " + bytes.length);
        String str = "";
        int len = bytes.length;
        for (int i = 0; i < len && i < 5; i++) {
            str += Integer.toHexString(bytes[i]) + " ";
        }
        Log.i("enc: " + tag + ": hex: " + str);
    }
}