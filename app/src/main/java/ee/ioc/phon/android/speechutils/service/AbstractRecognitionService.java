package ee.ioc.phon.android.speechutils.service;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

import androidx.annotation.RequiresPermission;

import java.io.IOException;
import java.util.ArrayList;

import ee.ioc.phon.android.speechutils.AudioCue;
import ee.ioc.phon.android.speechutils.AudioPauser;
import ee.ioc.phon.android.speechutils.AudioRecorder;
import ee.ioc.phon.android.speechutils.EncodedAudioRecorder;
import ee.ioc.phon.android.speechutils.Extras;
import ee.ioc.phon.android.speechutils.Log;
import ee.ioc.phon.android.speechutils.RawAudioRecorder;
import ee.ioc.phon.android.speechutils.utils.PreferenceUtils;

import static android.Manifest.permission.RECORD_AUDIO;

/**
 * Performs audio recording and is meant for cloud services.
 * About RemoteException see
 * http://stackoverflow.com/questions/3156389/android-remoteexceptions-and-services
 */
public abstract class AbstractRecognitionService extends RecognitionService {

    // Check the volume 10 times a second
    private static final int TASK_INTERVAL_VOL = 100;
    // Wait for 1/2 sec before starting to measure the volume
    private static final int TASK_DELAY_VOL = 500;

    private static final int TASK_INTERVAL_STOP = 1000;
    private static final int TASK_DELAY_STOP = 1000;

    private AudioCue mAudioCue;
    private AudioPauser mAudioPauser;
    private RecognitionService.Callback mListener;

    private AudioRecorder mRecorder;

    private final Handler mVolumeHandler = new Handler();
    private Runnable mShowVolumeTask;

    private final Handler mStopHandler = new Handler();
    private Runnable mStopTask;

    private Bundle mExtras;

    protected static Bundle toResultsBundle(String hypothesis) {
        ArrayList<String> hypotheses = new ArrayList<>();
        hypotheses.add(hypothesis);
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, hypotheses);
        return bundle;
    }

    protected static Bundle toResultsBundle(ArrayList<String> hypotheses, boolean isFinal) {
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, hypotheses);
        bundle.putBoolean(Extras.EXTRA_SEMI_FINAL, isFinal);
        return bundle;
    }

    /**
     * Configures the service based on the given intent extras.
     * Can result in an IOException, e.g. if building the server URL fails
     * (UnsupportedEncodingException, MalformedURLException).
     * TODO: generalize the exception
     */
    protected abstract void configure(Intent recognizerIntent) throws IOException;

    /**
     * Start sending audio to the server.
     */
    protected abstract void connect();

    /**
     * Stop sending audio to the server.
     */
    protected abstract void disconnect();

    /**
     * Returns the type of encoder to use. Subclasses must override this method if they want to
     * record in a non-raw format.
     *
     * @return type of encoder as string (e.g. "audio/x-flac")
     */
    protected String getEncoderType() {
        return null;
    }

    /**
     * @return Audio recorder
     */
    @RequiresPermission(RECORD_AUDIO)
    protected AudioRecorder getAudioRecorder() throws IOException {
        if (mRecorder == null) {
            mRecorder = createAudioRecorder(getEncoderType(), getSampleRate());
        }
        return mRecorder;
    }

    /**
     * Queries the preferences to find out if audio cues are switched on.
     * Different services can have different preferences.
     */
    protected boolean isAudioCues() {
        return false;
    }

    /**
     * Gets the sample rate used in the recorder.
     * Different services can use a different sample rate.
     */
    protected int getSampleRate() {
        return 16000;
    }

    /**
     * Gets the max number of milliseconds to record.
     */
    protected int getAutoStopAfterMillis() {
        return 1000 * 10000; // We record as long as the server allows
    }

    /**
     * Stop after a pause is detected.
     * This can be implemented either in the server or in the app.
     */
    protected boolean isAutoStopAfterPause() {
        return false;
    }

    /**
     * Tasks done after the recording has finished and the audio has been obtained.
     */
    protected void afterRecording(byte[] recording) {
        // Nothing to do, e.g. if the audio has already been sent to the server during recording
    }

    // TODO: remove this, we have already getAudioRecorder
    protected AudioRecorder getRecorder() {
        return mRecorder;
    }

    protected SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    public void onDestroy() {
        super.onDestroy();
        disconnectAndStopRecording();
    }

    /**
     * Starts recording and opens the connection to the server to start sending the recorded packages.
     */
    @RequiresPermission(RECORD_AUDIO)
    @Override
    protected void onStartListening(final Intent recognizerIntent, RecognitionService.Callback listener) {
        mListener = listener;
        Log.i("onStartListening");

        mExtras = recognizerIntent.getExtras();
        if (mExtras == null) {
            mExtras = new Bundle();
        }

        if (mExtras.containsKey(Extras.EXTRA_AUDIO_CUES)) {
            setAudioCuesEnabled(mExtras.getBoolean(Extras.EXTRA_AUDIO_CUES));
        } else {
            setAudioCuesEnabled(isAudioCues());
        }

        try {
            configure(recognizerIntent);
        } catch (IOException e) {
            onError(SpeechRecognizer.ERROR_CLIENT);
            return;
        }

        mAudioPauser = AudioPauser.createAudioPauser(this, true);
        Log.i("AudioPauser can mute stream: " + mAudioPauser.isMuteStream());
        mAudioPauser.pause();

        try {
            onReadyForSpeech(new Bundle());
            startRecord();
        } catch (IOException e) {
            onError(SpeechRecognizer.ERROR_AUDIO);
            return;
        }

        onBeginningOfSpeech();
        connect();
    }

    /**
     * Stops the recording and informs the server that no more packages are coming.
     */
    @Override
    protected void onStopListening(RecognitionService.Callback listener) {
        Log.i("onStopListening");
        onEndOfSpeech();
    }

    /**
     * Stops the recording and closes the connection to the server.
     */
    @Override
    protected void onCancel(RecognitionService.Callback listener) {
        Log.i("onCancel");
        disconnectAndStopRecording();
        // Send empty results if recognition is cancelled
        // TEST: if it works with Google Translate and Slide IT
        onResults(new Bundle());
    }


    /**
     * Calls onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) if server initiates close
     * without having received EOS. Otherwise simply shuts down the recorder and recognizer service.
     *
     * @param isEosSent true iff EOS was sent
     */
    public void handleFinish(boolean isEosSent) {
        if (isEosSent) {
            onCancel(mListener);
        } else {
            onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT);
        }
    }

    protected Bundle getExtras() {
        return mExtras;
    }

    protected void onReadyForSpeech(Bundle bundle) {
        if (mAudioCue != null) mAudioCue.playStartSoundAndSleep();
        try {
            mListener.readyForSpeech(bundle);
        } catch (RemoteException e) {
            // Ignored
        }
    }

    protected void onRmsChanged(float rms) {
        try {
            mListener.rmsChanged(rms);
        } catch (RemoteException e) {
            // Ignored
        }
    }

    protected void onError(int errorCode) {
        disconnectAndStopRecording();
        if (mAudioCue != null) mAudioCue.playErrorSound();
        try {
            mListener.error(errorCode);
        } catch (RemoteException e) {
            // Ignored
        }
    }

    protected void onResults(Bundle bundle) {
        disconnectAndStopRecording();
        try {
            mListener.results(bundle);
        } catch (RemoteException e) {
            // Ignored
        }
    }

    protected void onPartialResults(Bundle bundle) {
        try {
            mListener.partialResults(bundle);
        } catch (RemoteException e) {
            // Ignored
        }
    }

    protected void onBeginningOfSpeech() {
        try {
            mListener.beginningOfSpeech();
        } catch (RemoteException e) {
            // Ignored
        }
    }

    /**
     * Fires the endOfSpeech callback, provided that the recorder is currently recording.
     */
    protected void onEndOfSpeech() {
        if (mRecorder == null || mRecorder.getState() != AudioRecorder.State.RECORDING) {
            return;
        }
        byte[] recording;

        // TODO: make sure this call does not do too much work in the case of the
        // WebSocket-service which does not use the bytes in the end
        if (mRecorder instanceof EncodedAudioRecorder) {
            recording = ((EncodedAudioRecorder) mRecorder).consumeRecordingEnc();
        } else {
            recording = mRecorder.consumeRecording();
        }

        stopRecording0();
        if (mAudioCue != null) {
            mAudioCue.playStopSound();
        }
        try {
            mListener.endOfSpeech();
        } catch (RemoteException e) {
            // Ignored
        }
        afterRecording(recording);
    }

    protected void onBufferReceived(byte[] buffer) {
        try {
            mListener.bufferReceived(buffer);
        } catch (RemoteException e) {
            // Ignored
        }
    }

    /**
     * Return the server URL specified by the caller, or if this is missing then the URL
     * stored in the preferences, or if this is missing then the default URL.
     *
     * @param key          preference key to the server URL
     * @param defaultValue default URL to use if no URL is stored at the given key
     * @return server URL as string
     */
    protected String getServerUrl(int key, int defaultValue) {
        String url = getExtras().getString(Extras.EXTRA_SERVER_URL);
        if (url == null) {
            return PreferenceUtils.getPrefString(
                    getSharedPreferences(),
                    getResources(),
                    key,
                    defaultValue);
        }
        return url;
    }

    /**
     * Constructs a recorder based on the encoder type and sample rate. By default returns the raw
     * audio recorder. If an unsupported encoder is specified then throws an exception.
     */
    @RequiresPermission(RECORD_AUDIO)
    protected static AudioRecorder createAudioRecorder(String encoderType, int sampleRate) throws IOException {
        // TODO: take from an enum
        if ("audio/x-flac".equals(encoderType)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                return new EncodedAudioRecorder(sampleRate);
            }
            throw new IOException(encoderType + " not supported");
        }
        return new RawAudioRecorder(sampleRate);
    }


    /**
     * Starts recording.
     *
     * @throws IOException if there was an error, e.g. another app is currently recording
     */
    @RequiresPermission(RECORD_AUDIO)
    private void startRecord() throws IOException {
        mRecorder = getAudioRecorder();
        if (mRecorder.getState() == AudioRecorder.State.ERROR) {
            throw new IOException();
        }

        if (mRecorder.getState() != AudioRecorder.State.READY) {
            throw new IOException();
        }

        mRecorder.start();

        if (mRecorder.getState() != AudioRecorder.State.RECORDING) {
            throw new IOException();
        }

        // Monitor the volume level
        mShowVolumeTask = new Runnable() {
            public void run() {
                if (mRecorder != null) {
                    onRmsChanged(mRecorder.getRmsdb());
                    mVolumeHandler.postDelayed(this, TASK_INTERVAL_VOL);
                }
            }
        };

        mVolumeHandler.postDelayed(mShowVolumeTask, TASK_DELAY_VOL);

        // Time (in milliseconds since the boot) when the recording is going to be stopped
        final long timeToFinish = SystemClock.uptimeMillis() + getAutoStopAfterMillis();
        final boolean isAutoStopAfterPause = isAutoStopAfterPause();

        // Check if we should stop recording
        mStopTask = new Runnable() {
            public void run() {
                if (mRecorder != null) {
                    if (timeToFinish < SystemClock.uptimeMillis() || isAutoStopAfterPause && mRecorder.isPausing()) {
                        onEndOfSpeech();
                    } else {
                        mStopHandler.postDelayed(this, TASK_INTERVAL_STOP);
                    }
                }
            }
        };

        mStopHandler.postDelayed(mStopTask, TASK_DELAY_STOP);
    }


    private void stopRecording0() {
        releaseRecorder();
        mVolumeHandler.removeCallbacks(mShowVolumeTask);
        mStopHandler.removeCallbacks(mStopTask);
        if (mAudioPauser != null) mAudioPauser.resume();
    }


    private void releaseRecorder() {
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
    }


    private void setAudioCuesEnabled(boolean enabled) {
        if (enabled) {
            mAudioCue = new AudioCue(this);
        } else {
            mAudioCue = null;
        }
    }


    private void disconnectAndStopRecording() {
        disconnect();
        stopRecording0();
    }
}
