package com.ast2012a.clinicalmaster;

import android.content.Context;

public final class OfflineMode {
    private OfflineMode() {}

    public static final String PREFS = "settings_prefs";
    public static final String KEY_OFFLINE_ONLY = "offline_only";
    public static final String ERROR_MESSAGE = "Offline mode is on. No network request was made. / وضع بدون إنترنت مفعّل. لم يتم أي اتصال.";

    public static boolean isOnlineAllowed(Context ctx) {
        if (ctx == null) return false;
        return !ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_OFFLINE_ONLY, true);
    }

    public static boolean isOnlineAllowed() {
        return isOnlineAllowed(ClinicalMasterApp.get());
    }

    public static boolean isOfflineOnly(Context ctx) {
        return !isOnlineAllowed(ctx);
    }

    public static void setOfflineOnly(Context ctx, boolean on) {
        ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OFFLINE_ONLY, on).apply();
    }
}
