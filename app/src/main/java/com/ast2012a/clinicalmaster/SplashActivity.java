package com.ast2012a.clinicalmaster;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

/**
 * شاشة splash: علامة التطبيق (نفس أيقونة التطبيق) تُرسم بالحركة — شارة تقفز، خط نبضة
 * يُرسم، نجمة Semo AI تظهر مع موجة ضوء — ثم اسم التطبيق يصعد وحروفه تتقارب،
 * ثم اسم المطوّر. بعد اكتمال الحركة تنتقل لـ MainActivity بتلاشي مع تكبير خفيف.
 * ألوان الخلفية تتبع الوضع الليلي/النهاري (Theme.ClinicalMaster.Splash).
 */
public class SplashActivity extends AppCompatActivity {

    private static final long REVEAL_MS = 1700;
    private static final long AUTO_NAVIGATE_DELAY_MS = 2250;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable navigateRunnable;
    private SplashMarkView mark;
    private boolean leaving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mark = findViewById(R.id.splash_mark);
        View title = findViewById(R.id.splash_title);
        View credit = findViewById(R.id.splash_credit);
        final View root = findViewById(R.id.splash_root);

        mark.play(REVEAL_MS);

        // الاسم: يصعد من أسفل وتتقارب حروفه
        float dy = getResources().getDisplayMetrics().density * 18f;
        title.setTranslationY(dy);
        title.animate().alpha(1f).translationY(0f).setStartDelay(900).setDuration(650)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
        if (title instanceof android.widget.TextView) {
            ObjectAnimator sp = ObjectAnimator.ofFloat(title, "letterSpacing", 0.32f, 0.02f);
            sp.setStartDelay(900);
            sp.setDuration(900);
            sp.setInterpolator(new DecelerateInterpolator(1.6f));
            sp.start();
        }

        // اسم المطوّر
        credit.setTranslationY(dy * 0.6f);
        credit.animate().alpha(1f).translationY(0f).setStartDelay(1350).setDuration(550)
                .setInterpolator(new DecelerateInterpolator()).start();

        navigateRunnable = this::goToMain;
        handler.postDelayed(navigateRunnable, AUTO_NAVIGATE_DELAY_MS);
        root.setOnClickListener(v -> {
            handler.removeCallbacks(navigateRunnable);
            goToMain();
        });
    }

    private void goToMain() {
        if (isFinishing() || leaving) return;
        leaving = true;
        final View root = findViewById(R.id.splash_root);
        root.animate().alpha(0f).scaleX(1.05f).scaleY(1.05f).setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (isFinishing()) return;
                    startActivity(new Intent(this, MainActivity.class));
                    Motion.fade(this);
                    finish();
                }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (navigateRunnable != null) handler.removeCallbacks(navigateRunnable);
        if (mark != null) mark.stop();
    }
}
