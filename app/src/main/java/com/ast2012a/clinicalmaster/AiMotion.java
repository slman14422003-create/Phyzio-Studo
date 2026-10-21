package com.ast2012a.clinicalmaster;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

public final class AiMotion {

    private AiMotion() {}

    public static ValueAnimator reveal(final TextView tv, long maxMs, final Runnable done) {
        final CharSequence full = tv.getText();
        final int len = full.length();
        if (len == 0) {
            if (done != null) done.run();
            return null;
        }
        tv.setText(full, TextView.BufferType.SPANNABLE);
        final Spannable sp = (Spannable) tv.getText();
        final ForegroundColorSpan hide = new ForegroundColorSpan(Color.TRANSPARENT);
        sp.setSpan(hide, 0, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        long dur = Math.max(260L, Math.min(maxMs, len * 16L));
        ValueAnimator a = ValueAnimator.ofInt(0, len);
        a.setDuration(dur);
        a.setInterpolator(new LinearInterpolator());
        final int[] shown = {0};
        a.addUpdateListener(an -> {
            int n = (Integer) an.getAnimatedValue();
            while (n < len && !Character.isWhitespace(full.charAt(n))) n++;
            if (n == shown[0]) return;
            shown[0] = n;
            if (n >= len) sp.removeSpan(hide);
            else sp.setSpan(hide, n, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
                sp.removeSpan(hide);
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                sp.removeSpan(hide);
                if (!cancelled && done != null) done.run();
            }
        });
        a.start();
        return a;
    }

    public static void popIn(View v, long delay) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setScaleX(0.4f);
        v.setScaleY(0.4f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(320)
                .setInterpolator(new OvershootInterpolator(2.6f)).start();
    }

    public static void riseIn(View v, long delay, float dyDp, long duration) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(Ui.dp(v.getContext(), dyDp));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(duration)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    public static void bounce(View v) {
        v.animate().cancel();
        v.setScaleX(0.8f);
        v.setScaleY(0.8f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new OvershootInterpolator(3.4f)).start();
        Ui.tick(v);
    }

    public static void spin(View v) {
        v.animate().cancel();
        v.setRotation(0f);
        v.animate().rotationBy(360f).setDuration(520).setInterpolator(new DecelerateInterpolator(1.4f))
                .withEndAction(() -> v.setRotation(0f)).start();
        Ui.tick(v);
    }

    public static void wobble(View v) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "rotation", 0f, -16f, 14f, -9f, 6f, 0f);
        a.setDuration(440);
        a.start();
        Ui.tick(v);
    }

    public static void shake(View v) {
        float d = Ui.dp(v.getContext(), 8f);
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "translationX", 0f, -d, d, -d * 0.6f, d * 0.4f, 0f);
        a.setDuration(360);
        a.start();
        Ui.tick(v);
    }
}
