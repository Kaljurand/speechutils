package ee.ioc.phon.android.speechutils;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;

/**
 * Pauses the audio stream by requesting the audio focus and
 * muting the music stream.
 * <p/>
 * TODO: Test this is two interleaving instances of AudioPauser, e.g.
 * TTS starts playing and calls the AudioPauser, at the same time
 * the recognizer starts listening and also calls the AudioPauser.
 */
public class AudioPauser {

    private final boolean mIsMuteStream;
    private final AudioManager mAudioManager;
    private final OnAudioFocusChangeListener mAfChangeListener;
    private int mCurrentVolume = 0;
    private boolean isPausing = false;

    public AudioPauser(Context context) {
        this(context, true);
    }


    public AudioPauser(Context context, boolean isMuteStream) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mIsMuteStream = isMuteStream;

        mAfChangeListener = new OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
                Log.i("onAudioFocusChange" + focusChange);
            }
        };
    }


    /**
     * Requests audio focus with the goal of pausing any existing audio player.
     * Additionally mutes the music stream, since some audio players might
     * ignore the focus request.
     * In other words, during the pause no sound will be heard,
     * but whether the audio resumes from the same position after the pause
     * depends on the audio player.
     */
    public void pause() {
        if (!isPausing) {
            int result = mAudioManager.requestAudioFocus(mAfChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i("AUDIOFOCUS_REQUEST_GRANTED");
            }

            if (mIsMuteStream) {
                mCurrentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (mCurrentVolume > 0) {
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                }
            }
            isPausing = true;
        }
    }


    /**
     * Abandons audio focus and restores the audio volume.
     */
    public void resume() {
        if (isPausing) {
            mAudioManager.abandonAudioFocus(mAfChangeListener);
            if (mIsMuteStream && mCurrentVolume > 0) {
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mCurrentVolume, 0);
            }
            isPausing = false;
        }
    }

}