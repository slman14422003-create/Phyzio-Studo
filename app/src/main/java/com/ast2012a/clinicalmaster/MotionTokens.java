package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

public final class MotionTokens {

    public static final int DUR_FAST = 150;
    public static final int DUR_NORMAL = 250;
    public static final int DUR_SLOW = 400;

    public static final Interpolator ENTER = new DecelerateInterpolator(1.5f);
    public static final Interpolator EXIT = new AccelerateInterpolator(1.5f);
    public static final Interpolator CONFIRM = new OvershootInterpolator(1.6f);

    public static final long STAGGER_STEP = 30L;
    public static final int MAX_STAGGERED = 6;
    public static final int ENTER_OFFSET_DP = 12;

    private MotionTokens() {}

    public static float enterOffsetPx(Context c) {
        return ENTER_OFFSET_DP * c.getResources().getDisplayMetrics().density;
    }
}
