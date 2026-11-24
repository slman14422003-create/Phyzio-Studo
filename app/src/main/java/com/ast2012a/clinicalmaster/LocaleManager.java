package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public final class LocaleManager {

    public static final String LANG_SYSTEM = "system";
    public static final String LANG_AR = "ar";
    public static final String LANG_EN = "en";

    private static final String PREFS = "settings_prefs";
    private static final String KEY_LANGUAGE = "app_language";

    private LocaleManager() {
    }

    public static String get(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = p.getString(KEY_LANGUAGE, LANG_AR);
        if (LANG_SYSTEM.equals(value) || LANG_EN.equals(value)) return value;
        return LANG_AR;
    }

    public static void set(Context context, String language) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, language).apply();
        apply(language);
    }

    public static void applySaved(Context context) {
        String language = get(context);
        LocaleListCompat wanted = toList(language);
        if (!AppCompatDelegate.getApplicationLocales().equals(wanted)) {
            apply(language);
        } else if (!LANG_SYSTEM.equals(language)) {
            Locale.setDefault(Locale.forLanguageTag(language));
        }
    }

    public static Context wrap(Context base) {
        String language = get(base);
        if (LANG_SYSTEM.equals(language)) return base;
        Locale locale = Locale.forLanguageTag(language);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return base.createConfigurationContext(config);
    }

    public static int labelRes(String language) {
        if (LANG_EN.equals(language)) return R.string.ui_language_english;
        if (LANG_SYSTEM.equals(language)) return R.string.ui_language_system;
        return R.string.ui_language_arabic;
    }

    private static void apply(String language) {
        if (!LANG_SYSTEM.equals(language)) {
            Locale.setDefault(Locale.forLanguageTag(language));
        }
        AppCompatDelegate.setApplicationLocales(toList(language));
    }

    private static LocaleListCompat toList(String language) {
        if (LANG_SYSTEM.equals(language)) return LocaleListCompat.getEmptyLocaleList();
        return LocaleListCompat.forLanguageTags(language);
    }
}
