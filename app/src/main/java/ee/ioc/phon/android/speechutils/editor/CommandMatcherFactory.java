package ee.ioc.phon.android.speechutils.editor;

import android.content.ComponentName;

import java.util.regex.Pattern;

import ee.ioc.phon.android.speechutils.Log;


public class CommandMatcherFactory {

    public static CommandMatcher createCommandFilter(final String localeAsStr, final ComponentName serviceComponent, final ComponentName appComponent) {
        final String serviceClassName = serviceComponent == null ? null : serviceComponent.getClassName();
        final String appClassName = appComponent == null ? null : appComponent.getClassName();
        return new CommandMatcher() {
            @Override
            public boolean matches(Pattern localePattern, Pattern servicePattern, Pattern appPattern) {
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
                Log.i("match: context data: " + localeAsStr + " " + serviceClassName + " " + appClassName);
                Log.i("match: pattern: <" + localePattern + "> <" + servicePattern + "> <" + appPattern + ">");
                return true;
            }
        };
    }
}
