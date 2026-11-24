package com.ast2012a.clinicalmaster;

import android.app.Application;
import android.content.Context;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

/**
 * 1) يطبّق وضع الليلي/النهاري المحفوظ قبل رسم أي شاشة، لمنع أي وميض بصري.
 *    ملحوظة إصلاح: هذا الكلاس كان موجودًا لكنه لم يكن مسجّلًا في
 *    AndroidManifest.xml (بدون android:name على وسم application)، فكانت
 *    applySavedTheme() لا تُستدعى إطلاقًا عند فتح التطبيق من جديد - يعني
 *    اختيار "دائمًا ليلي/نهاري" من الإعدادات كان يُفقد فور إغلاق التطبيق
 *    وإعادة فتحه (يرجع لوضع النظام تلقائيًا). تم تسجيله الآن في المانفست.
 *
 * 2) يفعّل "Material You" الحقيقي (الألوان الديناميكية المستخرجة من خلفية
 *    شاشة المستخدم) على أندرويد 12 فأحدث. على الأجهزة الأقدم أو لو النظام
 *    لا يدعمها، يبقى التطبيق على هويته اللونية الطوبية الدافئة المعرّفة
 *    يدويًا في themes.xml بدون أي تغيير.
 */
public class ClinicalMasterApp extends Application {
    private static Application instance;

    public static Application get() {
        return instance;
    }

    private static int startedActivities = 0;

    /** true لو أي شاشة من التطبيق ظاهرة الآن (لتقرير فتح المُثبّت مباشرة أو بإشعار). */
    static boolean isInForeground() {
        return startedActivities > 0;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.wrap(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        LocaleManager.applySaved(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(android.app.Activity a) { startedActivities++; }
            @Override public void onActivityStopped(android.app.Activity a) { startedActivities = Math.max(0, startedActivities - 1); }
            @Override public void onActivityCreated(android.app.Activity a, android.os.Bundle b) { }
            @Override public void onActivityResumed(android.app.Activity a) { }
            @Override public void onActivityPaused(android.app.Activity a) { }
            @Override public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) { }
            @Override public void onActivityDestroyed(android.app.Activity a) { }
        });
        Motion.install(this);
        SelectionBar.install(this);
        CrashHandler.install(this);
        ThemeManager.applySavedTheme(this);

        // إعادة ضبط منبّه "جلسات اليوم" مع كل تشغيل (يغطي إيقاف التطبيق بالقوة)
        try {
            SessionReminder.schedule(this);
        } catch (Throwable ignored) {
        }

        // تهيئة مبكرة (في الخلفية) لمكتبة استخراج نص PDF - بدل ما أول سؤال
        // للمساعد الذكي يحتاج مستند سحابي ينتظر تحميل موارد PdfBox لحظتها.
        // آمنة تمامًا لو فشلت (مثلًا مفيش تخزين سحابي مضبوط أصلًا).
        new Thread(() -> DocumentTextExtractor.warmUp(getApplicationContext())).start();

        DynamicColorsOptions options = new DynamicColorsOptions.Builder()
                .setPrecondition((activity, theme) -> DynamicColors.isDynamicColorAvailable()
                        // شاشة splash مستثناة عمدًا: لازم تفضل بنفس لون علامة
                        // Claude الثابت (claude_orange) دايمًا، مش لون ديناميكي
                        // مستخرج من خلفية جهاز المستخدم.
                        && !(activity instanceof SplashActivity))
                .build();
        DynamicColors.applyToActivitiesIfAvailable(this, options);
    }
}
