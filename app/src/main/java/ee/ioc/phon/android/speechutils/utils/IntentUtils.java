package ee.ioc.phon.android.speechutils.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.SparseIntArray;
import android.widget.Toast;

import org.json.JSONException;

import java.util.List;

import ee.ioc.phon.android.speechutils.Log;
import ee.ioc.phon.android.speechutils.R;

public final class IntentUtils {

    private IntentUtils() {
    }

    /**
     * @return table that maps SpeechRecognizer error codes to RecognizerIntent error codes
     */
    public static SparseIntArray createErrorCodesServiceToIntent() {
        SparseIntArray errorCodes = new SparseIntArray();
        errorCodes.put(SpeechRecognizer.ERROR_AUDIO, RecognizerIntent.RESULT_AUDIO_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_CLIENT, RecognizerIntent.RESULT_CLIENT_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, RecognizerIntent.RESULT_CLIENT_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_NETWORK, RecognizerIntent.RESULT_NETWORK_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_NETWORK_TIMEOUT, RecognizerIntent.RESULT_NETWORK_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_NO_MATCH, RecognizerIntent.RESULT_NO_MATCH);
        errorCodes.put(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, RecognizerIntent.RESULT_SERVER_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_SERVER, RecognizerIntent.RESULT_SERVER_ERROR);
        errorCodes.put(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, RecognizerIntent.RESULT_NO_MATCH);
        return errorCodes;
    }

    public static PendingIntent getPendingIntent(Bundle extras) {
        Parcelable extraResultsPendingIntentAsParceable = extras.getParcelable(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT);
        if (extraResultsPendingIntentAsParceable != null) {
            //PendingIntent.readPendingIntentOrNullFromParcel(mExtraResultsPendingIntent);
            if (extraResultsPendingIntentAsParceable instanceof PendingIntent) {
                return (PendingIntent) extraResultsPendingIntentAsParceable;
            }
        }
        return null;
    }

    public static Intent getAppIntent(Context c, String packageName) {
        PackageManager pm = c.getPackageManager();
        return pm.getLaunchIntentForPackage(packageName);
    }

    public static void startActivityFromJson(Activity activity, CharSequence query) {
        try {
            startActivityIfAvailable(activity, JsonUtils.createIntent(query));
        } catch (JSONException e) {
            Log.i("startSearchActivity: JSON: " + e.getMessage());
            startActivitySearch(activity, query);
        }
    }

    /**
     * Constructs a list of search intents.
     * The first one that can be handled by the device is launched.
     * In split-screen mode, launch the activity into the other screen. Test this by:
     * 1. Launch Kõnele, 2. Start split-screen, 3. Press Kõnele mic button and speak,
     * 4. The results should be loaded into the other window.
     *
     * @param activity activity
     * @param query    search query
     */
    private static void startActivitySearch(Activity activity, CharSequence query) {
        // TODO: how to pass the search query to ACTION_ASSIST
        // TODO: maybe use SearchManager instead
        //Intent intent0 = new Intent(Intent.ACTION_ASSIST);
        //intent0.putExtra(Intent.EXTRA_ASSIST_CONTEXT, new Bundle());
        //intent0.putExtra(SearchManager.QUERY, query);
        //intent0.putExtra(Intent.EXTRA_ASSIST_INPUT_HINT_KEYBOARD, false);
        //intent0.putExtra(Intent.EXTRA_ASSIST_PACKAGE, context.getPackageName());
        startActivityIfAvailable(activity,
                getSearchIntent(Intent.ACTION_WEB_SEARCH, query),
                getSearchIntent(Intent.ACTION_SEARCH, query));
    }

    public static boolean startActivityIfAvailable(Context context, Intent... intents) {
        PackageManager mgr = context.getPackageManager();
        try {
            for (Intent intent : intents) {
                if (isActivityAvailable(mgr, intent)) {
                    // TODO: is it sensible to always start activity for result,
                    // even if the activity is not designed to return a result
                    context.startActivity(intent);
                    //activity.startActivityForResult(intent, 2);
                    return true;
                } else {
                    Log.i("startActivityIfAvailable: not available: " + intent);
                }
            }
            Toast.makeText(context, R.string.errorFailedLaunchIntent, Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            // This happens if the user constructs an intent for which we do not have a
            // permission, e.g. the CALL intent.
            Log.i("startActivityIfAvailable: " + e.getMessage());
            Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
        return false;
    }

    /**
     * Checks whether a speech recognition service is available on the system. If this method
     * returns {@code false}, {@link SpeechRecognizer#createSpeechRecognizer(Context, ComponentName)}
     * will fail.
     * Similar to {@link SpeechRecognizer#isRecognitionAvailable(Context)} but supports
     * restricting the intent query by component name.
     * <p/>
     * TODO: propose to add this to SpeechRecognizer
     * TODO: clarify what does "will fail" mean
     *
     * @param context       with which {@code SpeechRecognizer} will be created
     * @param componentName of the recognition service
     * @return {@code true} if recognition is available, {@code false} otherwise
     */
    public static boolean isRecognitionAvailable(final Context context, ComponentName componentName) {
        Intent intent = new Intent(RecognitionService.SERVICE_INTERFACE);
        intent.setComponent(componentName);
        final List<ResolveInfo> list = context.getPackageManager().queryIntentServices(intent, 0);
        return list != null && list.size() != 0;
    }

    private static boolean isActivityAvailable(PackageManager mgr, Intent intent) {
        List<ResolveInfo> list = mgr.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    private static Intent getSearchIntent(String action, CharSequence query) {
        Intent intent = new Intent(action);
        intent.putExtra(SearchManager.QUERY, query);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        }
        return intent;
    }
}