package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;

public class SemoOrbView extends TickerView {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int accent;
    private final int gold;
    private float angle;
    private float speed = 24f;
    private float targetSpeed = 24f;
    private float kick;

    public SemoOrbView(Context context) {
        this(context, null);
    }

    public SemoOrbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = context.getColor(R.color.claude_orange);
        gold = context.getColor(R.color.accent_gold);
    }

    public void setThinking(boolean thinking) {
        targetSpeed = thinking ? 220f : 24f;
    }

    public void kick() {
        kick = 1f;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 150), widthSpec),
                resolveSize(Ui.dp(getContext(), 150), heightSpec));
    }

    @Override
    protected void step(float dt) {
        speed += (targetSpeed - speed) * Math.min(1f, dt * 4f);
        angle += speed * dt;
        kick = Math.max(0f, kick - dt * 2.4f);
    }

    private static int alpha(int c, float a) {
        return Color.argb(Math.round(255 * clamp01(a)), Color.red(c), Color.green(c), Color.blue(c));
    }

    private void sparkle(Canvas c, float x, float y, float s, int color) {
        path.reset();
        path.moveTo(x, y - s);
        path.quadTo(x, y, x + s, y);
        path.quadTo(x, y, x, y + s);
        path.quadTo(x, y, x - s, y);
        path.quadTo(x, y, x, y - s);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawPath(path, paint);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();
        float r = Math.min(w, h) / 2f;
        float cx = w / 2f;
        float cy = h / 2f + (float) Math.sin(time * 1.6f) * r * 0.04f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(alpha(accent, 0.07f));
        c.drawCircle(cx, cy, r * 0.92f, paint);
        paint.setColor(alpha(accent, 0.09f));
        c.drawCircle(cx, cy, r * 0.68f, paint);
        paint.setColor(alpha(accent, 0.12f));
        c.drawCircle(cx, cy, r * 0.5f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 1.6f));
        float rate = targetSpeed > 100f ? 0.9f : 0.35f;
        for (int i = 0; i < 3; i++) {
            float k = frac(time * rate + i / 3f);
            paint.setColor(alpha(accent, (1f - k) * 0.35f));
            c.drawCircle(cx, cy, r * (0.34f + 0.62f * k), paint);
        }

        float breath = 1f + 0.06f * (float) Math.sin(time * 2.1f) + 0.18f * kick;
        c.save();
        c.translate(cx, cy);
        c.rotate(angle);
        c.scale(breath, breath);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(r * 0.105f);
        paint.setColor(accent);
        float len = r * 0.36f;
        for (int i = 0; i < 3; i++) {
            c.save();
            c.rotate(i * 60f);
            c.drawLine(0f, -len, 0f, len, paint);
            c.restore();
        }
        c.restore();

        float orbit = r * 0.8f;
        for (int i = 0; i < 6; i++) {
            float a = (float) Math.toRadians(-angle * 0.45f + i * 60f);
            float x = cx + (float) Math.cos(a) * orbit;
            float y = cy + (float) Math.sin(a) * orbit;
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 3f + i * 1.3f);
            if (i % 2 == 0) {
                sparkle(c, x, y, r * (0.05f + 0.05f * tw), alpha(i == 2 ? gold : accent, 0.4f + 0.6f * tw));
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(alpha(accent, 0.25f + 0.5f * tw));
                c.drawCircle(x, y, r * 0.025f + r * 0.015f * tw, paint);
            }
        }
    }
}
