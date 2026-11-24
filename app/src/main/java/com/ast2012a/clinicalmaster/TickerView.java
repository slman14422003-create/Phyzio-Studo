package com.ast2012a.clinicalmaster;

import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public abstract class TickerView extends View {

    private ValueAnimator ticker;
    private long last;
    protected float time;
    protected float dt;

    public TickerView(Context context) {
        this(context, null);
    }

    public TickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    protected boolean autoStart() {
        return true;
    }

    protected void step(float dt) {
    }

    protected boolean motionAllowed() {
        try {
            return Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Exception e) {
            return true;
        }
    }

    protected void startTicker() {
        if (ticker != null && ticker.isRunning()) return;
        if (!isAttachedToWindow() || !motionAllowed()) return;
        last = System.nanoTime();
        ticker = ValueAnimator.ofFloat(0f, 1f);
        ticker.setDuration(1000);
        ticker.setRepeatCount(ValueAnimator.INFINITE);
        ticker.setInterpolator(new LinearInterpolator());
        ticker.addUpdateListener(a -> {
            long n = System.nanoTime();
            dt = Math.min(0.05f, (n - last) / 1e9f);
            last = n;
            time += dt;
            step(dt);
            invalidate();
        });
        ticker.start();
    }

    protected void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (autoStart()) startTicker();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTicker();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (!autoStart()) return;
        if (isVisible) startTicker();
        else stopTicker();
    }

    protected static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    protected static float frac(float v) {
        return v - (float) Math.floor(v);
    }
}
