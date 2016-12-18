package ee.ioc.phon.android.speechutils.editor;

import android.content.ComponentName;

import java.util.regex.Pattern;

import ee.ioc.phon.android.speechutils.Log;


public class CommandMatcherFactory {

    public static CommandMatcher createCommandFilter(final String localeAsStr, final ComponentName serviceComponent, final ComponentName appComponent) {
        final String serviceClassName = serviceComponent == null ? null : serviceComponent.getClassName();
        // The Kõnele launcher (whose calling activity == null) can be matched using ":".
        final String appClassName = appComponent == null ? ":" : appComponent.getClassName();
        return new CommandMatcher() {
            @Override
            public boolean matches(Pattern localePattern, Pattern servicePattern, Pattern appPattern) {
                Log.i("matches?: pattern: <" + localePattern + "> <" + servicePattern + "> <" + appPattern + ">");
                if (localeAsStr != null && localePattern != null) {
                    if (!localePattern.matcher(localeAsStr).find()) {
                        return false;
                    }
                }
                if (serviceClassName != null && servicePattern != null) {
                    if (!servicePattern.matcher(serviceClassName).find()) {
                        return false;
                    }
                }
                if (appClassName != null && appPattern != null) {
                    if (!appPattern.matcher(appClassName).find()) {
                        return false;
                    }
                }
                Log.i("matches: " + localeAsStr + " " + serviceClassName + " " + appClassName);
                return true;
            }
        };
    }
}