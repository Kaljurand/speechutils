package ee.ioc.phon.android.speechutils.editor;

import android.content.ComponentName;

import java.util.regex.Pattern;

import ee.ioc.phon.android.speechutils.Log;


public class CommandMatcherFactory {

    public static CommandMatcher createCommandFilter(final String localeAsStr, final ComponentName serviceComponent, final ComponentName appComponent) {
        return new CommandMatcher() {
            @Override
            public boolean matches(Pattern localePattern, Pattern servicePattern, Pattern appPattern) {
                if (localeAsStr != null && localePattern != null) {
                    if (!localePattern.matcher(localeAsStr).find()) {
                        return false;
                    }
                }
                if (serviceComponent != null && servicePattern != null) {
                    if (!servicePattern.matcher(serviceComponent.getClassName()).find()) {
                        return false;
                    }
                }
                if (appComponent != null && appPattern != null) {
                    if (!appPattern.matcher(appComponent.getClassName()).find()) {
                        return false;
                    }
                }
                Log.i("match: context data: " + localeAsStr + " " + serviceComponent + " " + appComponent);
                Log.i("match: pattern: <" + localePattern + "> <" + servicePattern + "> <" + appPattern + ">");
                return true;
            }
        };
    }
}
