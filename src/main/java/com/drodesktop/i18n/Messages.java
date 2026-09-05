package com.drodesktop.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class Messages {
    private static volatile ResourceBundle bundle = ResourceBundle.getBundle("i18n.Messages", new Locale("de"));

    private Messages() {
    }

    public static void setLanguage(String languageCode) {
        Locale locale = "en".equalsIgnoreCase(languageCode) ? Locale.ENGLISH : new Locale("de");
        bundle = ResourceBundle.getBundle("i18n.Messages", locale);
    }

    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException ex) {
            return key;
        }
    }

    public static String get(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }
}
