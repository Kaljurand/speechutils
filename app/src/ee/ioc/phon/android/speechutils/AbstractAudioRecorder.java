/*
 * Copyright 2011-2015, Institute of Cybernetics at Tallinn University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ee.ioc.phon.android.speechutils;

import android.media.AudioFormat;
import android.media.MediaRecorder;

/**
 * <p>Records raw audio using SpeechRecord and stores it into a byte array as</p>
 * <ul>
 * <li>signed</li>
 * <li>16-bit</li>
 * <li>native endian</li>
 * <li>mono</li>
 * <li>16kHz (recommended, but a different sample rate can be specified in the constructor)</li>
 * </ul>
 * <p/>
 * <p>For example, the corresponding <code>arecord</code> settings are</p>
 * <p/>
 * <pre>
 * arecord --file-type raw --format=S16_LE --channels 1 --rate 16000
 * arecord --file-type raw --format=S16_BE --channels 1 --rate 16000 (possibly)
 * </pre>
 * <p/>
 * TODO: maybe use: ByteArrayOutputStream
 *
 * @author Kaarel Kaljurand
 */
public abstract class AbstractAudioRecorder implements AudioRecorder {

    static final int DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    static final int DEFAULT_SAMPLE_RATE = 16000;

    static final int RESOLUTION = AudioFormat.ENCODING_PCM_16BIT;
    static final short RESOLUTION_IN_BYTES = 2;

    // Number of channels (MONO = 1, STEREO = 2)
    static final short CHANNELS = 1;

    //static final ByteOrder NATIVE_ORDER = ByteOrder.nativeOrder();

    private SpeechRecord mRecorder = null;

    private double mAvgEnergy = 0;

    private final int mSampleRate;
    private final int mOneSec;

    // Recorder state
    private State mState;

    // Buffer size
    private int mBufferSize;

    // Number of frames written to byte array on each output
    private int mFramePeriod;

    // The complete space into which the recording in written.
    // Its maximum length is about:
    // 2 (bytes) * 1 (channels) * 30 (max rec time in seconds) * 44100 (times per second) = 2 646 000 bytes
    // but typically is:
    // 2 (bytes) * 1 (channels) * 20 (max rec time in seconds) * 16000 (times per second) = 640 000 bytes
    private final byte[] mRecording;

    // TODO: use: mRecording.length instead
    private int mRecordedLength = 0;

    // The number of bytes the client has already consumed
    private int mConsumedLength = 0;

    // Buffer for output
    private byte[] mBuffer;

    abstract void recorderLoop(SpeechRecord speechRecord);

    protected AbstractAudioRecorder(int audioSource, int sampleRate) {
        mSampleRate = sampleRate;
        // E.g. 1 second of 16kHz 16-bit mono audio takes 32000 bytes.
        mOneSec = RESOLUTION_IN_BYTES * CHANNELS * mSampleRate;
        // TODO: replace 35 with the max length of the recording
        mRecording = new byte[mOneSec * 35];
        try {
            setBufferSizeAndFramePeriod();
            mRecorder = new SpeechRecord(audioSource, mSampleRate, AudioFormat.CHANNEL_IN_MONO, RESOLUTION, mBufferSize, false, false, false);
            if (getSpeechRecordState() != SpeechRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("SpeechRecord initialization failed");
            }
            mBuffer = new byte[mFramePeriod * RESOLUTION_IN_BYTES * CHANNELS];
            setState(State.READY);
        } catch (Exception e) {
            if (e.getMessage() == null) {
                handleError("Unknown error occurred while initializing recording");
            } else {
                handleError(e.getMessage());
            }
        }
    }

    public byte[] getEncodedRecording() {
        return null;
    }

    int getSampleRate() {
        return mSampleRate;
    }

    int read(SpeechRecord recorder) {
        // public int read (byte[] audioData, int offsetInBytes, int sizeInBytes)
        int numberOfBytes = recorder.read(mBuffer, 0, mBuffer.length); // Fill buffer

        // Some error checking
        if (numberOfBytes == SpeechRecord.ERROR_INVALID_OPERATION) {
            Log.e("The SpeechRecord object was not properly initialized");
            return -1;
        } else if (numberOfBytes == SpeechRecord.ERROR_BAD_VALUE) {
            Log.e("The parameters do not resolve to valid data and indexes.");
            return -2;
        } else if (numberOfBytes > mBuffer.length) {
            Log.e("Read more bytes than is buffer length:" + numberOfBytes + ": " + mBuffer.length);
            return -3;
        } else if (numberOfBytes == 0) {
            Log.e("Read zero bytes");
            return -4;
        }
        // Everything seems to be OK, adding the buffer to the recording.
        add(mBuffer);
        return 0;
    }


    void setBufferSizeAndFramePeriod() {
        int minBufferSizeInBytes = SpeechRecord.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_IN_MONO, RESOLUTION);
        if (minBufferSizeInBytes == SpeechRecord.ERROR_BAD_VALUE) {
            throw new IllegalArgumentException("SpeechRecord.getMinBufferSize: parameters not supported by hardware");
        } else if (minBufferSizeInBytes == SpeechRecord.ERROR) {
            Log.e("SpeechRecord.getMinBufferSize: unable to query hardware for output properties");
            minBufferSizeInBytes = mSampleRate * (120 / 1000) * RESOLUTION_IN_BYTES * CHANNELS;
        }
        mBufferSize = 2 * minBufferSizeInBytes;
        mFramePeriod = mBufferSize / (2 * RESOLUTION_IN_BYTES * CHANNELS);
        Log.i("SpeechRecord buffer size: " + mBufferSize + ", min size = " + minBufferSizeInBytes);
    }


    /**
     * @return recorder state
     */
    public State getState() {
        return mState;
    }

    void setState(State state) {
        mState = state;
    }


    /**
     * @return bytes that have been recorded since the beginning
     */
    public byte[] getCompleteRecording() {
        return getCurrentRecording(0);
    }


    /**
     * @return bytes that have been recorded since the beginning, with wav-header
     */
    public byte[] getCompleteRecordingAsWav() {
        return getRecordingAsWav(getCompleteRecording(), mSampleRate);
    }


    public static byte[] getRecordingAsWav(byte[] pcm, int sampleRate) {
        int headerLen = 44;
        int byteRate = sampleRate * RESOLUTION_IN_BYTES; // mSampleRate*(16/8)*1 ???
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
        header[22] = (byte) CHANNELS;
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
        header[34] = (byte) 8 * RESOLUTION_IN_BYTES;  // bits per sample
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

    /**
     * @return bytes that have been recorded since this method was last called
     */
    public synchronized byte[] consumeRecording() {
        byte[] bytes = getCurrentRecording(mConsumedLength);
        mConsumedLength = mRecordedLength;
        return bytes;
    }

    byte[] getCurrentRecording(int startPos) {
        int len = getLength() - startPos;
        byte[] bytes = new byte[len];
        System.arraycopy(mRecording, startPos, bytes, 0, len);
        Log.i("Copied from: " + startPos + ": " + bytes.length + " bytes");
        return bytes;
    }

    int getConsumedLength() {
        return mConsumedLength;
    }

    void setConsumedLength(int len) {
        mConsumedLength = len;
    }

    void setRecordedLength(int len) {
        mRecordedLength = len;
    }

    public int getLength() {
        return mRecordedLength;
    }


    /**
     * @return <code>true</code> iff a speech-ending pause has occurred at the end of the recorded data
     */
    public boolean isPausing() {
        double pauseScore = getPauseScore();
        Log.i("Pause score: " + pauseScore);
        return pauseScore > 7;
    }


    /**
     * @return volume indicator that shows the average volume of the last read buffer
     */
    public float getRmsdb() {
        long sumOfSquares = getRms(mRecordedLength, mBuffer.length);
        double rootMeanSquare = Math.sqrt(sumOfSquares / (mBuffer.length / 2));
        if (rootMeanSquare > 1) {
            // TODO: why 10?
            return (float) (10 * Math.log10(rootMeanSquare));
        }
        return 0;
    }


    /**
     * <p>In order to calculate if the user has stopped speaking we take the
     * data from the last second of the recording, map it to a number
     * and compare this number to the numbers obtained previously. We
     * return a confidence score (0-INF) of a longer pause having occurred in the
     * speech input.</p>
     * <p/>
     * <p>TODO: base the implementation on some well-known technique.</p>
     *
     * @return positive value which the caller can use to determine if there is a pause
     */
    private double getPauseScore() {
        long t2 = getRms(mRecordedLength, mOneSec);
        if (t2 == 0) {
            return 0;
        }
        double t = mAvgEnergy / t2;
        mAvgEnergy = (2 * mAvgEnergy + t2) / 3;
        return t;
    }


    /**
     * <p>Stops the recording (if needed) and releases the resources.
     * The object can no longer be used and the reference should be
     * set to null after a call to release().</p>
     */
    public synchronized void release() {
        if (mRecorder != null) {
            if (mRecorder.getRecordingState() == SpeechRecord.RECORDSTATE_RECORDING) {
                stop();
            }
            mRecorder.release();
            mRecorder = null;
        }
    }

    /**
     * <p>Starts the recording, and sets the state to RECORDING.</p>
     */
    public void start() {
        if (getSpeechRecordState() == SpeechRecord.STATE_INITIALIZED) {
            mRecorder.startRecording();
            if (mRecorder.getRecordingState() == SpeechRecord.RECORDSTATE_RECORDING) {
                setState(State.RECORDING);
                new Thread() {
                    public void run() {
                        recorderLoop(mRecorder);
                    }
                }.start();
            } else {
                handleError("startRecording() failed");
            }
        } else {
            handleError("start() called on illegal state");
        }
    }


    /**
     * <p>Stops the recording, and sets the state to STOPPED.
     * If stopping fails then sets the state to ERROR.</p>
     */
    public void stop() {
        // We check the underlying SpeechRecord state trying to avoid IllegalStateException.
        // If it still occurs then we catch it.
        if (getSpeechRecordState() == SpeechRecord.STATE_INITIALIZED &&
                mRecorder.getRecordingState() == SpeechRecord.RECORDSTATE_RECORDING) {
            try {
                mRecorder.stop();
                setState(State.STOPPED);
            } catch (IllegalStateException e) {
                handleError("native stop() called in illegal state: " + e.getMessage());
            }
        } else {
            handleError("stop() called in illegal state");
        }
    }


    /**
     * <p>Copy the given byte array into the total recording array.</p>
     * <p/>
     * <p>The total recording array has been pre-allocated (e.g. for 35 seconds of audio).
     * If it gets full then the recording is stopped.</p>
     *
     * @param buffer audio buffer
     */
    void add(byte[] buffer) {
        if (mRecording.length >= mRecordedLength + buffer.length) {
            // arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
            System.arraycopy(buffer, 0, mRecording, mRecordedLength, buffer.length);
            mRecordedLength += buffer.length;
        } else {
            // This also happens on the emulator for some reason
            handleError("Recorder buffer overflow: " + mRecordedLength);
        }
    }


    private long getRms(int end, int span) {
        int begin = end - span;
        if (begin < 0) {
            begin = 0;
        }
        // make sure begin is even
        if (0 != (begin % 2)) {
            begin++;
        }

        long sum = 0;
        for (int i = begin; i < end; i += 2) {
            short curSample = getShort(mRecording[i], mRecording[i + 1]);
            sum += curSample * curSample;
        }
        return sum;
    }


    /*
     * Converts two bytes to a short (assuming little endian).
     * TODO: We don't need the whole short, just take the 2nd byte (the more significant one)
     * TODO: Most Android devices are little endian?
     */
    private static short getShort(byte argB1, byte argB2) {
        //if (NATIVE_ORDER.equals(ByteOrder.BIG_ENDIAN)) {
        //    return (short) ((argB1 << 8) | argB2);
        //}
        return (short) (argB1 | (argB2 << 8));
    }


    void handleError(String msg) {
        release();
        setState(State.ERROR);
        Log.e(msg);
    }

    int getSpeechRecordState() {
        if (mRecorder == null) {
            return SpeechRecord.STATE_UNINITIALIZED;
        }
        return mRecorder.getState();
    }
}