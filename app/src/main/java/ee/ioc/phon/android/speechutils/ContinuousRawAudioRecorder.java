package ee.ioc.phon.android.speechutils;

import java.util.concurrent.atomic.AtomicBoolean;

import ee.ioc.phon.android.speechutils.utils.AudioUtils;

public class ContinuousRawAudioRecorder extends AbstractAudioRecorder {

    private static final int DEFAULT_BUFFER_LENGTH_IN_MILLIS = 2000;
    private static final String LOG_FILTER = "continuous-recorder: ";

    private SessionStartPointer mSessionStartPointer = SessionStartPointer.beginningOfBuffer();
    private AtomicBoolean mRecordingToFile = new AtomicBoolean(false);

    public static class SessionStartPointer {

        private int mSessionStartPointerMillis;
        private static SessionStartPointer mBeginningOfBufferPosition = new SessionStartPointer(Integer.MIN_VALUE);
        private static SessionStartPointer mNowPosition = new SessionStartPointer(0);

        private SessionStartPointer(int sessionStartPointerMillis) {
            setSessionStartPointerMillis(sessionStartPointerMillis);
        }

        private void setSessionStartPointerMillis(int sessionStartPointerMillis) {
            // how many millis we should go back should always be a positive number
            mSessionStartPointerMillis = Math.abs(sessionStartPointerMillis);
        }

        int getSessionStartPointerMillis() {
            // in case that no one set the buffer length, return start pointer as now
            if (this == mBeginningOfBufferPosition && mSessionStartPointerMillis == Integer.MIN_VALUE)
                return mNowPosition.getSessionStartPointerMillis();

            // in case that the requested start pointer is bigger than the buffer, return the buffer size
            if (this != mBeginningOfBufferPosition && mSessionStartPointerMillis > mBeginningOfBufferPosition.getSessionStartPointerMillis())
                return mBeginningOfBufferPosition.getSessionStartPointerMillis();

            return mSessionStartPointerMillis;
        }

        static void setRecordingBufferLengthMillis(int recordingBufferLengthMillis) {
            mBeginningOfBufferPosition.setSessionStartPointerMillis(recordingBufferLengthMillis);
        }

        public static SessionStartPointer beginningOfBuffer() {
            return mBeginningOfBufferPosition;
        }

        public static SessionStartPointer now() {
            return mNowPosition;
        }

        public static SessionStartPointer someMillisBack(int millisBackToStartTheSessionFrom) {
            return new SessionStartPointer(millisBackToStartTheSessionFrom);
        }

        public static SessionStartPointer someSecondsBack(int secondsBackToStartTheSessionFrom) {
            return new SessionStartPointer(secondsBackToStartTheSessionFrom * 1000);
        }
    }

    public ContinuousRawAudioRecorder(int audioSource, int sampleRate, int recordingBufferLengthMillis) {
        super(audioSource, sampleRate, recordingBufferLengthMillis, true);

        // this is very important. We introduce the buffer length to the SessionStartPointer object
        SessionStartPointer.setRecordingBufferLengthMillis(recordingBufferLengthMillis);

        try {
            int bufferSize = getBufferSize();
            int framePeriod = bufferSize / (2 * RESOLUTION_IN_BYTES * CHANNELS);
            createRecorder(audioSource, sampleRate, bufferSize);
            createBuffer(framePeriod);
            setState(State.READY);
        } catch (Exception e) {
            if (e.getMessage() == null) {
                handleError("Unknown error occurred while initializing recorder");
            } else {
                handleError(e.getMessage());
            }
        }
    }

    public ContinuousRawAudioRecorder(int sampleRate) {
        this(DEFAULT_AUDIO_SOURCE, sampleRate, DEFAULT_BUFFER_LENGTH_IN_MILLIS);
    }

    public ContinuousRawAudioRecorder() {
        this(DEFAULT_AUDIO_SOURCE, DEFAULT_SAMPLE_RATE, DEFAULT_BUFFER_LENGTH_IN_MILLIS);
    }

    public String getWsArgs() {
        return "?content-type=audio/x-raw,+layout=(string)interleaved,+rate=(int)" + getSampleRate() + ",+format=(string)S16LE,+channels=(int)1";
    }

    public ContinuousRawAudioRecorder setSessionStartPointer(SessionStartPointer sessionStartPointer) {
        mSessionStartPointer = sessionStartPointer;
        return this;
    }

    private int calculateNumOfSamplesToGoBack(int startPos) {
        // if the consumed session is not the same as the recorded session
        // get the data from the beginning of the buffer/desired session start pointer
        if (!isRecordedSessionSameAsConsumedSession()) {
            Log.i(LOG_FILTER + "Recorded session and consumed session are NOT the same. Grabbing the data from the session start position");
            return getNumOfSamplesIn(mSessionStartPointer.getSessionStartPointerMillis());
        }

        Log.i(LOG_FILTER + "Recorded session and consumed session are the same. Working according to the consumed pointer");
        if (startPos == getLength()) {
            Log.i(LOG_FILTER + "Consumed session pointer still points to the recorded session pointer. Nothing to do or to return");
            return -1;
        }

        int numOfSamplesToGoBack = getLength() - startPos;
        if (numOfSamplesToGoBack < 0) // cyclic buffer case
            numOfSamplesToGoBack += mRecording.length;

        Log.i(LOG_FILTER + "Consumed session pointer is behind the recorded session pointer by: " + numOfSamplesToGoBack + " samples");
        return numOfSamplesToGoBack;
    }

    @Override
    protected byte[] getCurrentRecording(int startPos) {

        int numOfSamplesToGoBack = calculateNumOfSamplesToGoBack(startPos);
        if (numOfSamplesToGoBack <= 0) {
            Log.i(LOG_FILTER + "There are no samples that we need to take from the recording");
            return null;
        }

        byte[] buffer = new byte[numOfSamplesToGoBack];
        int potentialStartSample = getLength() - numOfSamplesToGoBack;

        if (potentialStartSample >= 0) {
            Log.i(LOG_FILTER + "Start sample in the recording is a positive one. Copying from position: " + potentialStartSample + ", " + numOfSamplesToGoBack + " bytes");
            System.arraycopy(mRecording, potentialStartSample, buffer, 0, numOfSamplesToGoBack);
        }
        else {
            if (!mRecordingBufferIsFullWithData) {
                Log.i(LOG_FILTER + "Start sample in the recording is a negative one. The buffer did not pass one cycle yet. Copying from position: 0, " + getLength() + " bytes");
                System.arraycopy(mRecording, 0, buffer, 0, getLength());
            }
            else {
                // the potential start sample is out of the boundaries of the array to the negative side
                potentialStartSample = Math.abs(potentialStartSample);
                Log.i(LOG_FILTER + "Start sample in the recording is a negative one. The buffer passed at least one cycle. Copying from position: " + (mRecording.length - potentialStartSample) + ", " + potentialStartSample + " bytes and from position: 0, " + getLength() + " bytes");
                System.arraycopy(mRecording, mRecording.length - potentialStartSample, buffer, 0, potentialStartSample);
                System.arraycopy(mRecording, 0, buffer, potentialStartSample, getLength());
            }
        }

        return buffer;
    }

    private byte[] pcmToWav(byte[] pcm) {
        return AudioUtils.getRecordingAsWav(pcm, getSampleRate(), RESOLUTION_IN_BYTES, CHANNELS);
    }

    private byte[] createWavHeader(int pcmDataLength) {
        return AudioUtils.getWavHeader(pcmDataLength, getSampleRate(), RESOLUTION_IN_BYTES, CHANNELS);
    }

    public void dumpBufferToWavFile(String wavFileFullPath) {
        AudioUtils.saveWavToFile(wavFileFullPath, pcmToWav(consumeRecording()), false);
    }

    public void startRecording(final String wavFileFullPath) {
        if (!mRecordingToFile.compareAndSet(false, true))
            return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                // in case that someone stopped the recording before the thread could start
                if (!mRecordingToFile.get())
                    return;

                int pcmDataLength = 0;
                byte[] pcmData;
                AtomicBoolean firstBuffer = new AtomicBoolean(true);

                while(mRecordingToFile.get()) {

                    // No need to endlessly poll the recording. It will work without the sleep well but
                    // every 50ms is also good (keeps the CPU happier than without the sleep)
                    try {
                        Thread.sleep(50L);
                    }
                    catch (InterruptedException e) {
                        mRecordingToFile.set(false);
                        return;
                    }

                    pcmData = consumeRecording();
                    if (pcmData == null || pcmData.length == 0)
                        continue;

                    pcmDataLength += pcmData.length;
                    if (firstBuffer.compareAndSet(true, false))
                        AudioUtils.saveWavToFile(wavFileFullPath, pcmToWav(pcmData), false);
                    else
                        AudioUtils.saveWavToFile(wavFileFullPath, pcmData, true);
                }

                //now rewrite the wav header according to the new size
                AudioUtils.saveWavHeaderToFile(wavFileFullPath, createWavHeader(pcmDataLength));
            }
        }).start();
    }

    public void stopRecording() {
        mRecordingToFile.compareAndSet(true, false);
    }
}
