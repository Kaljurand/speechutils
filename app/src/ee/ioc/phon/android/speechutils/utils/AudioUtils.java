package ee.ioc.phon.android.speechutils.utils;

import java.util.List;

public class AudioUtils {

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
}