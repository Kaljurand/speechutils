package ee.ioc.phon.android.speechutils.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.List;

import ee.ioc.phon.android.speechutils.R;

public class MicButton extends ImageButton {

    public enum State {
        // Initial state
        INIT,
        // An attempt was made to start the recording/transcription, but unclear if it succeeded
        WAITING,
        // We are listening (if there is human speech)
        LISTENING,
        // We are recording and sending the (speech) audio for transcription
        RECORDING,
        // Recording has stopped but not all the recorded audio has been processed yet
        TRANSCRIBING,
        // An error has occurred
        ERROR
    }

    // TODO: take these from some device specific configuration
    private static final float DB_MIN = 15.0f;
    private static final float DB_MAX = 30.0f;

    private Drawable mDrawableMic;
    private Drawable mDrawableMicTranscribing;
    private Drawable mDrawableMicWaiting;

    private List<Drawable> mVolumeLevels;

    private Animation mAnimFadeInOutInf;

    private int mVolumeLevel = 0;
    private int mMaxLevel;

    public MicButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (!isInEditMode()) {
            init(context);
        }
    }

    public MicButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            init(context);
        }
    }

    public MicButton(Context context) {
        super(context);
        if (!isInEditMode()) {
            init(context);
        }
    }

    public void setState(State state) {
        switch (state) {
            case INIT:
            case ERROR:
                setEnabled(true);
                clearAnimation();
                setBackgroundDrawable(mDrawableMic);
                break;
            case WAITING:
                setEnabled(false);
                setBackgroundDrawable(mDrawableMicWaiting);
                break;
            case RECORDING:
                setEnabled(true);
                break;
            case LISTENING:
                setEnabled(true);
                setBackgroundDrawable(mVolumeLevels.get(0));
                break;
            case TRANSCRIBING:
                setEnabled(true);
                setBackgroundDrawable(mDrawableMicTranscribing);
                startAnimation(mAnimFadeInOutInf);
                break;
            default:
                break;
        }
    }


    public void setVolumeLevel(float rmsdB) {
        int index = (int) ((rmsdB - DB_MIN) / (DB_MAX - DB_MIN) * mMaxLevel);
        int level = Math.min(Math.max(0, index), mMaxLevel);
        if (level != mVolumeLevel) {
            mVolumeLevel = level;
            setBackgroundDrawable(mVolumeLevels.get(level));
        }
    }

    private void initAnimations(Context context) {
        Resources res = getResources();
        mDrawableMic = res.getDrawable(R.drawable.button_mic);
        mDrawableMicTranscribing = res.getDrawable(R.drawable.button_mic_transcribing);
        mDrawableMicWaiting = res.getDrawable(R.drawable.button_mic_waiting);

        mVolumeLevels = new ArrayList<>();
        mVolumeLevels.add(res.getDrawable(R.drawable.button_mic_recording_0));
        mVolumeLevels.add(res.getDrawable(R.drawable.button_mic_recording_1));
        mVolumeLevels.add(res.getDrawable(R.drawable.button_mic_recording_2));
        mVolumeLevels.add(res.getDrawable(R.drawable.button_mic_recording_3));
        mMaxLevel = mVolumeLevels.size() - 1;

        mAnimFadeInOutInf = AnimationUtils.loadAnimation(context, R.anim.fade_inout_inf);
    }

    private void init(Context context) {
        initAnimations(context);

        // Vibrate when the microphone key is pressed down
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    // TODO: what is the diff between KEYBOARD_TAP and the other constants?
                    // TODO: does not seem to work on Android 7.1
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                return false;
            }
        });
    }
}