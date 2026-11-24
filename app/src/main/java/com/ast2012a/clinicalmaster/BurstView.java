package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BurstView extends TickerView {

    private static final class P {
        float x, y, vx, vy, life, max, size;
        boolean star;
        int color;
    }

    private static final class Ring {
        float x, y, age, max, maxR;
        int color;
    }

    private final List<P> parts = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random rnd = new Random();
    private final int accent;
    private final int gold;
    private final int[] loc = new int[2];
    private final int[] myLoc = new int[2];

    public BurstView(Context context) {
        this(context, null);
    }

    public BurstView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = context.getColor(R.color.claude_orange);
        gold = context.getColor(R.color.accent_gold);
        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected boolean autoStart() {
        return false;
    }

    private float[] center(View anchor) {
        anchor.getLocationInWindow(loc);
        getLocationInWindow(myLoc);
        return new float[]{loc[0] - myLoc[0] + anchor.getWidth() / 2f, loc[1] - myLoc[1] + anchor.getHeight() / 2f};
    }

    public void burstAt(View anchor, int count) {
        if (anchor == null || !motionAllowed()) return;
        float[] c = center(anchor);
        float unit = Ui.dp(getContext(), 1f);
        for (int i = 0; i < count; i++) {
            P p = new P();
            float a = (float) (rnd.nextFloat() * Math.PI * 2.0);
            float v = (60f + rnd.nextFloat() * 140f) * unit;
            p.x = c[0];
            p.y = c[1];
            p.vx = (float) Math.cos(a) * v;
            p.vy = (float) Math.sin(a) * v - 30f * unit;
            p.max = 0.55f + rnd.nextFloat() * 0.45f;
            p.size = (2.2f + rnd.nextFloat() * 3.2f) * unit;
            p.star = rnd.nextInt(3) == 0;
            p.color = rnd.nextInt(4) == 0 ? gold : accent;
            parts.add(p);
        }
        startTicker();
        invalidate();
    }

    public void ringAt(View anchor) {
        if (anchor == null || !motionAllowed()) return;
        float[] c = center(anchor);
        Ring r = new Ring();
        r.x = c[0];
        r.y = c[1];
        r.max = 0.6f;
        r.maxR = Math.max(anchor.getWidth(), anchor.getHeight()) * 1.1f;
        r.color = accent;
        rings.add(r);
        startTicker();
        invalidate();
    }

    @Override
    protected void step(float dt) {
        float g = 260f * Ui.dp(getContext(), 1f);
        for (int i = parts.size() - 1; i >= 0; i--) {
            P p = parts.get(i);
            p.life += dt;
            p.vy += g * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= 0.985f;
            if (p.life >= p.max) parts.remove(i);
        }
        for (int i = rings.size() - 1; i >= 0; i--) {
            Ring r = rings.get(i);
            r.age += dt;
            if (r.age >= r.max) rings.remove(i);
        }
        if (parts.isEmpty() && rings.isEmpty()) stopTicker();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 2f));
        for (Ring r : rings) {
            float k = clamp01(r.age / r.max);
            paint.setColor(Color.argb(Math.round(150 * (1f - k)), Color.red(r.color), Color.green(r.color), Color.blue(r.color)));
            c.drawCircle(r.x, r.y, r.maxR * (0.35f + 0.65f * k), paint);
        }
        paint.setStyle(Paint.Style.FILL);
        for (P p : parts) {
            float k = clamp01(p.life / p.max);
            paint.setColor(Color.argb(Math.round(255 * (1f - k)), Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
            float s = p.size * (1f - 0.4f * k);
            if (p.star) {
                path.reset();
                path.moveTo(p.x, p.y - s * 1.8f);
                path.quadTo(p.x, p.y, p.x + s * 1.8f, p.y);
                path.quadTo(p.x, p.y, p.x, p.y + s * 1.8f);
                path.quadTo(p.x, p.y, p.x - s * 1.8f, p.y);
                path.quadTo(p.x, p.y, p.x, p.y - s * 1.8f);
                path.close();
                c.drawPath(path, paint);
            } else {
                c.drawCircle(p.x, p.y, s, paint);
            }
        }
    }
}
