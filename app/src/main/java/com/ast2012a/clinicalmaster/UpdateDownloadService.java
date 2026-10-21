package com.ast2012a.clinicalmaster;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * تنزيل التحديث في الخلفية: خدمة أمامية (dataSync) مع إشعار تقدّم فيه زر إلغاء.
 * عند الاكتمال: لو التطبيق مفتوح يُفتح مُثبّت النظام مباشرة، وإلا يصل إشعار
 * «اضغط للتثبيت». التنزيل لا يتأثر بإغلاق الشاشة أو الخروج من التطبيق.
 */
public class UpdateDownloadService extends Service {

    static final String ACTION_START = "com.ast2012a.clinicalmaster.UPDATE_START";
    static final String ACTION_CANCEL = "com.ast2012a.clinicalmaster.UPDATE_CANCEL";
    private static final String CH_PROGRESS = "update_progress";
    private static final String CH_READY = "update_ready";
    private static final int ID_PROGRESS = 7101;
    private static final int ID_RESULT = 7102;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile boolean running = false;

    static void start(Context ctx, UpdateManager.Result r) {
        if (UpdateManager.isDownloading()) return;
        Intent i = new Intent(ctx, UpdateDownloadService.class).setAction(ACTION_START);
        i.putExtras(UpdateManager.toBundle(r));
        ContextCompat.startForegroundService(ctx, i);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelled.set(true);
            return START_NOT_STICKY;
        }
        if (running || intent.getExtras() == null) return START_NOT_STICKY;
        final UpdateManager.Result r = UpdateManager.fromBundle(intent.getExtras());
        running = true;
        cancelled.set(false);
        UpdateManager.setDownloading(true);
        createChannels();
        startAsForeground(progressNotification(r, 0, true, ""));

        new Thread(() -> work(r), "update-download").start();
        return START_NOT_STICKY;
    }

    private void startAsForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(ID_PROGRESS, n);
        }
    }

    private void work(UpdateManager.Result r) {
        final Context app = getApplicationContext();
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        final long[] last = {0};
        try {
            File apk = UpdateManager.download(app, r, cancelled, (st, done, total) -> {
                long now = SystemClock.uptimeMillis();
                if (now - last[0] < 600 && st == UpdateManager.STAGE_DOWNLOAD) return;
                last[0] = now;
                Notification n;
                if (st == UpdateManager.STAGE_DOWNLOAD && total > 0) {
                    int pct = (int) Math.min(100, done * 100 / total);
                    n = progressNotification(r, pct, false,
                            UpdateManager.mb(done) + " / " + UpdateManager.mb(total));
                } else if (st == UpdateManager.STAGE_VERIFY) {
                    n = progressNotification(r, 100, true, "جارٍ التحقق من سلامة الملف…");
                } else {
                    n = progressNotification(r, 0, true, "جارٍ تجهيز الملف…");
                }
                try {
                    nm.notify(ID_PROGRESS, n);
                } catch (Exception ignored) {
                }
            });
            if (apk == null || cancelled.get()) {
                UpdateManager.clearDir(UpdateManager.updatesDir(app));
                finish(true);
                return;
            }
            onDownloaded(app, nm, apk, r);
        } catch (Exception e) {
            UpdateManager.clearDir(UpdateManager.updatesDir(app));
            String reason = e.getMessage() == null ? "خطأ غير معروف" : e.getMessage();
            finish(true);
            postResult(nm, "تعذّر تنزيل التحديث", reason, null);
            return;
        }
        finish(true);
    }

    private void onDownloaded(Context app, NotificationManager nm, File apk, UpdateManager.Result r) {
        if (UpdateManager.signerMismatch(app, apk)) {
            UpdateManager.clearDir(UpdateManager.updatesDir(app));
            PendingIntent open = PendingIntent.getActivity(app, 1,
                    new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateManager.releasesUrl(r)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            postResult(nm, "لا يمكن التثبيت فوق النسخة الحالية",
                    "ملف التحديث موقّع بمفتاح مختلف. اضغط لفتح صفحة الإصدار وتثبيته يدويًا بعد نسخ احتياطي.", open);
            return;
        }
        try {
            Intent install = UpdateManager.buildInstallIntent(app, apk, r);
            if (ClinicalMasterApp.isInForeground()) {
                app.startActivity(install);
            }
            PendingIntent tap = PendingIntent.getActivity(app, 2, install,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            postResult(nm, "التحديث " + r.versionName + " جاهز",
                    "اضغط هنا لبدء التثبيت.", tap);
        } catch (Exception e) {
            postResult(nm, "تعذّر فتح المُثبّت",
                    "تم تنزيل التحديث لكن تعذّر فتح مُثبّت النظام. جرّب من صفحة الإصدارات على GitHub.", null);
        }
    }

    private void finish(boolean removeProgress) {
        UpdateManager.setDownloading(false);
        running = false;
        stopForeground(removeProgress ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
        stopSelf();
    }

    private void postResult(NotificationManager nm, String title, String text, PendingIntent tap) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH_READY)
                .setSmallIcon(R.drawable.ic_sparkle)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        if (tap != null) b.setContentIntent(tap);
        try {
            nm.notify(ID_RESULT, b.build());
        } catch (Exception ignored) {
        }
    }

    private Notification progressNotification(UpdateManager.Result r, int pct, boolean indeterminate, String sub) {
        Intent cancel = new Intent(this, UpdateDownloadService.class).setAction(ACTION_CANCEL);
        PendingIntent cancelPi = PendingIntent.getService(this, 3, cancel,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH_PROGRESS)
                .setSmallIcon(R.drawable.ic_sparkle)
                .setContentTitle("تنزيل التحديث " + r.versionName)
                .setContentText(indeterminate ? sub : pct + "%  ·  " + sub)
                .setProgress(100, pct, indeterminate)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, "إلغاء", cancelPi);
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH_PROGRESS, "تنزيل التحديثات",
                NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_READY, "جاهزية التحديث",
                NotificationManager.IMPORTANCE_HIGH));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UpdateManager.setDownloading(false);
    }
}
