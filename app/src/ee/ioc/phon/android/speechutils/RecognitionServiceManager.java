package ee.ioc.phon.android.speechutils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RecognitionServiceManager {
    private static String SEPARATOR = ";";
    private Set<String> mInitiallySelectedCombos = new HashSet<>();
    private Set<String> mCombosExcluded = new HashSet<>();

    public interface Listener {
        void onComplete(List<String> combos, Set<String> selectedCombos);
    }

    /**
     * @return true iff a RecognitionService with the given component name is installed
     */
    public static boolean isRecognitionServiceInstalled(PackageManager pm, ComponentName componentName) {
        List<ResolveInfo> services = pm.queryIntentServices(
                new Intent(RecognitionService.SERVICE_INTERFACE), 0);
        for (ResolveInfo ri : services) {
            ServiceInfo si = ri.serviceInfo;
            if (si == null) {
                Log.i("serviceInfo == null");
                continue;
            }
            if (componentName.equals(new ComponentName(si.packageName, si.name))) {
                return true;
            }
        }
        return false;
    }

    /**
     * On LOLLIPOP we use a builtin to parse the locale string, and return
     * the name of the locale in the language of the current locale. In pre-LOLLIPOP we just return
     * the formal name (e.g. "et-ee"), because the Locale-constructor is not able to parse it.
     *
     * @param localeAsStr Formal name of the locale, e.g. "et-ee"
     * @return The name of the locale in the language of the current locale
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static String makeLangLabel(String localeAsStr) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Locale.forLanguageTag(localeAsStr).getDisplayName();
        }
        return localeAsStr;
    }

    public static String[] getServiceAndLang(String str) {
        return TextUtils.split(str, SEPARATOR);
    }

    /**
     * @param str string like {@code ee.ioc.phon.android.speak/.HttpRecognitionService;et-ee}
     * @return ComponentName in the input string
     */
    public static ComponentName getComponentName(String str) {
        String[] splits = getServiceAndLang(str);
        return ComponentName.unflattenFromString(splits[0]);
    }

    public static String getServiceLabel(Context context, String service) {
        ComponentName recognizerComponentName = ComponentName.unflattenFromString(service);
        return getServiceLabel(context, recognizerComponentName);
    }

    public static String getServiceLabel(Context context, ComponentName recognizerComponentName) {
        String recognizer = "[?]";
        PackageManager pm = context.getPackageManager();
        if (recognizerComponentName != null) {
            try {
                ServiceInfo si = pm.getServiceInfo(recognizerComponentName, 0);
                recognizer = si.loadLabel(pm).toString();
            } catch (PackageManager.NameNotFoundException e) {
                // ignored
            }
        }
        return recognizer;
    }

    public static Pair<String, String> getLabel(Context context, String comboAsString) {
        String recognizer = "[?]";
        String language = "[?]";
        String[] splits = TextUtils.split(comboAsString, SEPARATOR);
        if (splits.length > 0) {
            PackageManager pm = context.getPackageManager();
            ComponentName recognizerComponentName = ComponentName.unflattenFromString(splits[0]);
            if (recognizerComponentName != null) {
                try {
                    ServiceInfo si = pm.getServiceInfo(recognizerComponentName, 0);
                    recognizer = si.loadLabel(pm).toString();
                } catch (PackageManager.NameNotFoundException e) {
                    // ignored
                }
            }
        }
        if (splits.length > 1) {
            language = makeLangLabel(splits[1]);
        }
        return new Pair<>(recognizer, language);
    }


    public void setCombosExcluded(Set<String> set) {
        mCombosExcluded = set;
    }

    public void setInitiallySelectedCombos(Set<String> set) {
        mInitiallySelectedCombos = set;
    }

    /**
     * @return list of currently installed RecognitionService component names flattened to short strings
     */
    public List<String> getServices(PackageManager pm) {
        List<String> services = new ArrayList<>();
        int flags = 0;
        //int flags = PackageManager.GET_META_DATA;
        List<ResolveInfo> infos = pm.queryIntentServices(
                new Intent(RecognitionService.SERVICE_INTERFACE), flags);

        for (ResolveInfo ri : infos) {
            ServiceInfo si = ri.serviceInfo;
            if (si == null) {
                Log.i("serviceInfo == null");
                continue;
            }
            String pkg = si.packageName;
            String cls = si.name;
            // TODO: process si.metaData
            String component = (new ComponentName(pkg, cls)).flattenToShortString();
            if (!mCombosExcluded.contains(component)) {
                services.add(component);
            }
        }
        return services;
    }

    /**
     * Collect together the languages supported by the given services and call back once done.
     */
    public void populateCombos(Activity activity, final Listener listener) {
        List<String> services = getServices(activity.getPackageManager());
        populateCombos(activity, services, listener);
    }

    public void populateCombos(Activity activity, List<String> services, final Listener listener) {
        populateCombos(activity, services, 0, listener, new ArrayList<String>(), new HashSet<String>());
    }

    public void populateCombos(Activity activity, String service, final Listener listener) {
        final List<String> services = new ArrayList<>();
        services.add(service);
        populateCombos(activity, services, listener);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private void populateCombos(final Activity activity, final List<String> services, final int counter, final Listener listener,
                                final List<String> combos, final Set<String> selectedCombos) {

        if (services.size() == counter) {
            listener.onComplete(combos, selectedCombos);
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS);
        // TODO: this seems to be only for activities that implement ACTION_WEB_SEARCH
        //Intent intent = RecognizerIntent.getVoiceDetailsIntent(this);

        final String service = services.get(counter);
        ComponentName serviceComponent = ComponentName.unflattenFromString(service);
        if (serviceComponent != null) {
            intent.setPackage(serviceComponent.getPackageName());
            // TODO: ideally we would like to query the component, because the package might
            // contain services (= components) with different capabilities.
            //intent.setComponent(serviceComponent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
            // This is needed to include newly installed apps or stopped apps
            // as receivers of the broadcast.
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        }

        activity.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                // Service that does not report which languages it supports
                if (getResultCode() != Activity.RESULT_OK) {
                    Log.i(combos.size() + ") NO LANG: " + service);
                    combos.add(service);
                    populateCombos(activity, services, counter + 1, listener, combos, selectedCombos);
                    return;
                }

                Bundle results = getResultExtras(true);

                // Supported languages
                String prefLang = results.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE);
                ArrayList<CharSequence> allLangs = results.getCharSequenceArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES);

                Log.i("Supported langs: " + prefLang + ": " + allLangs);
                if (allLangs == null) {
                    allLangs = new ArrayList<>();
                }
                // We add the preferred language to the list of supported languages, if not already there.
                if (prefLang != null && !allLangs.contains(prefLang)) {
                    allLangs.add(prefLang);
                }

                for (CharSequence lang : allLangs) {
                    String combo = service + SEPARATOR + lang;
                    if (!mCombosExcluded.contains(combo)) {
                        Log.i(combos.size() + ") " + combo);
                        combos.add(combo);
                        if (mInitiallySelectedCombos.contains(combo)) {
                            selectedCombos.add(combo);
                        }
                    }
                }

                populateCombos(activity, services, counter + 1, listener, combos, selectedCombos);
            }
        }, null, Activity.RESULT_OK, null, null);
    }
}