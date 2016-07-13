package ee.ioc.phon.android.speechutils.editor;

import android.content.ComponentName;

import java.util.regex.Pattern;


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
                return true;
            }
        };
    }
}
