package com.ast2012a.clinicalmaster;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class IllustrationView extends View {

    public static final int PULSE = 0;
    public static final int SPINE = 1;
    public static final int EXERCISE = 2;
    public static final int CALENDAR = 3;
    public static final int PATIENTS = 4;
    public static final int AI = 5;
    public static final int CLOUD = 6;
    public static final int SEARCH = 7;
    public static final int GEARS = 8;
    public static final int BOOK = 9;
    public static final int STAR = 10;
    public static final int DOC = 11;
    public static final int CLIPBOARD = 12;
    public static final int SHIELD = 13;

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private int scene = PULSE;
    private float t;
    private ValueAnimator animator;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private int cAccent;
    private int cInk;
    private int cSoft;
    private int cLine;
    private int cCard;
    private int cGreen;
    private int cGold;

    private float unit = 1f;
    private float span = 100f;
    private Shader edgeShader;

    public IllustrationView(Context context) {
        this(context, null);
    }

    public IllustrationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IllustrationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        cAccent = context.getColor(R.color.claude_orange);
        cInk = context.getColor(R.color.text_secondary);
        cSoft = context.getColor(R.color.m3_primary_container);
        cLine = context.getColor(R.color.glass_border);
        cCard = context.getColor(R.color.glass_fill_strong);
        cGreen = context.getColor(R.color.accent_green);
        cGold = context.getColor(R.color.accent_gold);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.IllustrationView);
            scene = a.getInt(R.styleable.IllustrationView_scene, PULSE);
            a.recycle();
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setScene(int scene) {
        this.scene = scene;
        invalidate();
    }

    public int getScene() {
        return scene;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 260), widthSpec),
                resolveSize(Ui.dp(getContext(), 140), heightSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        unit = Math.max(1f, h / 100f);
        span = w / unit;
        edgeShader = new LinearGradient(-span / 2f, 0f, span / 2f, 0f,
                new int[]{withAlpha(cAccent, 0f), cAccent, cAccent, withAlpha(cAccent, 0f)},
                new float[]{0f, 0.28f, 0.72f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnim();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnim();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) startAnim();
        else stopAnim();
    }

    private boolean motionAllowed() {
        try {
            return Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Exception e) {
            return true;
        }
    }

    private void startAnim() {
        if (animator != null && animator.isRunning()) return;
        if (!isAttachedToWindow() || !motionAllowed()) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(4200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = a.getAnimatedFraction();
            invalidate();
        });
        animator.start();
    }

    private void stopAnim() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static float wave(float p) {
        return (float) (0.5 - 0.5 * Math.cos(TWO_PI * p));
    }

    private static float ease(float x) {
        x = clamp(x);
        return x * x * (3f - 2f * x);
    }

    private static float overshoot(float x) {
        x = clamp(x);
        float s = 1.9f;
        float k = x - 1f;
        return k * k * ((s + 1f) * k + s) + 1f;
    }

    private static int withAlpha(int color, float a) {
        return Color.argb(Math.round(Color.alpha(color) * clamp(a)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int mix(int a, int b, float f) {
        f = clamp(f);
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }

    private Paint F(int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(null);
        fill.setColor(color);
        return fill;
    }

    private Paint S(int color, float w) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(w);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setShader(null);
        stroke.setColor(color);
        return stroke;
    }

    private void glow(Canvas c, float x, float y, float r) {
        c.drawCircle(x, y, r, F(withAlpha(cSoft, 0.35f)));
        c.drawCircle(x, y, r * 0.78f, F(withAlpha(cSoft, 0.45f)));
        c.drawCircle(x, y, r * 0.56f, F(withAlpha(cSoft, 0.6f)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(unit, unit);
        switch (scene) {
            case SPINE:
                drawSpine(canvas);
                break;
            case EXERCISE:
                drawExercise(canvas);
                break;
            case CALENDAR:
                drawCalendar(canvas);
                break;
            case PATIENTS:
                drawPatients(canvas);
                break;
            case AI:
                drawAi(canvas);
                break;
            case CLOUD:
                drawCloud(canvas);
                break;
            case SEARCH:
                drawSearch(canvas);
                break;
            case GEARS:
                drawGears(canvas);
                break;
            case BOOK:
                drawBook(canvas);
                break;
            case STAR:
                drawStar(canvas);
                break;
            case DOC:
                drawDoc(canvas);
                break;
            case CLIPBOARD:
                drawClipboard(canvas);
                break;
            case SHIELD:
                drawShield(canvas);
                break;
            default:
                drawPulse(canvas);
                break;
        }
        canvas.restore();
    }

    private float ecg(float x) {
        double v = 0;
        v += -5.0 * Math.exp(-Math.pow((x - 0.18) / 0.03, 2));
        v += 5.0 * Math.exp(-Math.pow((x - 0.42) / 0.008, 2));
        v += -30.0 * Math.exp(-Math.pow((x - 0.47) / 0.008, 2));
        v += 10.0 * Math.exp(-Math.pow((x - 0.52) / 0.01, 2));
        v += -8.0 * Math.exp(-Math.pow((x - 0.74) / 0.04, 2));
        return (float) v;
    }

    private void drawPulse(Canvas c) {
        glow(c, 0, 0, 46);
        float period = 64f;
        float off = t * period;
        float x0 = -span / 2f;
        path.reset();
        boolean first = true;
        for (float x = x0; x <= span / 2f; x += 1.5f) {
            float px = frac((x - x0 + off) / period);
            float y = 16f + ecg(px);
            if (first) {
                path.moveTo(x, y);
                first = false;
            } else {
                path.lineTo(x, y);
            }
        }
        Paint p = S(cAccent, 2.4f);
        p.setShader(edgeShader);
        c.drawPath(path, p);
        stroke.setShader(null);

        float beat = (float) Math.pow(Math.max(0.0, Math.sin(TWO_PI * t * 2f)), 6.0);
        float s = 1f + 0.16f * beat;
        c.save();
        c.translate(0f, -22f);
        c.scale(s, s);
        path.reset();
        path.moveTo(0f, 12f);
        path.cubicTo(-18f, -1f, -15f, -18f, 0f, -9f);
        path.cubicTo(15f, -18f, 18f, -1f, 0f, 12f);
        c.drawPath(path, F(cAccent));
        c.restore();
        c.drawCircle(0f, -22f, 16f + 12f * frac(t * 2f), S(withAlpha(cAccent, 0.35f * (1f - frac(t * 2f))), 1.6f));
    }

    private void drawSpine(Canvas c) {
        glow(c, 0, 0, 46);
        int n = 7;
        float pos = frac(t * 2f);
        float tri = pos < 0.5f ? pos * 2f : (1f - pos) * 2f;
        float hi = ease(tri) * (n - 1);
        for (int i = 0; i < n; i++) {
            float y = -38f + i * 12.6f;
            float x = 7f * (float) Math.sin(TWO_PI * t + i * 0.55f);
            float wd = 30f - Math.abs(i - 3) * 2.5f;
            float k = Math.max(0f, 1f - Math.abs(i - hi));
            rect.set(x - wd / 2f, y - 4.2f, x + wd / 2f, y + 4.2f);
            c.drawRoundRect(rect, 3.5f, 3.5f, F(mix(withAlpha(cInk, 0.45f), cAccent, k)));
            if (k > 0.2f) {
                c.drawCircle(x + wd / 2f + 4f, y, 1.6f + 2f * k, F(withAlpha(cAccent, 0.5f * k)));
            }
        }
        float ring = frac(t * 2f);
        float hy = -38f + hi * 12.6f;
        c.drawCircle(0f, hy, 14f + 14f * ring, S(withAlpha(cAccent, 0.4f * (1f - ring)), 1.4f));
    }

    private void drawExercise(Canvas c) {
        glow(c, 0, 0, 46);
        float a = wave(frac(t * 2f));
        float arm = (float) Math.toRadians(20f + a * 140f);
        float leg = (float) Math.toRadians(5f + a * 20f);
        float bob = -3f * a;
        c.drawOval(-18f - 8f * a, 36f, 18f + 8f * a, 41f, F(withAlpha(cInk, 0.18f)));
        c.save();
        c.translate(0f, bob);
        Paint p = S(cInk, 4.4f);
        c.drawLine(0f, -20f, 0f, 8f, p);
        for (int s = -1; s <= 1; s += 2) {
            c.drawLine(0f, -17f, s * 24f * (float) Math.sin(arm), -17f + 24f * (float) Math.cos(arm), p);
            c.drawLine(0f, 8f, s * 30f * (float) Math.sin(leg), 8f + 30f * (float) Math.cos(leg), p);
        }
        c.drawCircle(0f, -30f, 8f, F(cAccent));
        c.restore();
        for (int i = 0; i < 3; i++) {
            float k = clamp(frac(t * 2f) * 3f - i);
            c.drawCircle(34f + i * 8f, -28f, 2.4f + 1.6f * k, F(mix(withAlpha(cInk, 0.3f), cAccent, k)));
        }
    }

    private void drawCalendar(Canvas c) {
        glow(c, 0, 0, 46);
        rect.set(-32f, -34f, 32f, 36f);
        c.drawRoundRect(rect, 7f, 7f, F(cCard));
        c.drawRoundRect(rect, 7f, 7f, S(cLine, 1.4f));
        path.reset();
        path.addRoundRect(new RectF(-32f, -34f, 32f, -18f), new float[]{7f, 7f, 7f, 7f, 0f, 0f, 0f, 0f}, Path.Direction.CW);
        c.drawPath(path, F(cAccent));
        for (int i = -1; i <= 1; i += 2) {
            rect.set(i * 14f - 1.8f, -40f, i * 14f + 1.8f, -29f);
            c.drawRoundRect(rect, 1.8f, 1.8f, F(cInk));
        }
        float p = t * 15f;
        float fade = p < 12.5f ? 1f : clamp(1f - (p - 12.5f) / 2f);
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 4; col++) {
                int idx = r * 4 + col;
                float x = -21f + col * 14f;
                float y = -6f + r * 14f;
                float s = clamp(p - idx);
                c.drawCircle(x, y, 3.4f, F(withAlpha(cInk, 0.22f)));
                if (s > 0f && fade > 0f) {
                    float rad = 5.4f * overshoot(s);
                    c.drawCircle(x, y, rad, F(withAlpha(cAccent, fade)));
                    if (s > 0.7f) {
                        path.reset();
                        path.moveTo(x - 2.2f, y);
                        path.lineTo(x - 0.5f, y + 1.8f);
                        path.lineTo(x + 2.4f, y - 1.8f);
                        c.drawPath(path, S(withAlpha(Color.WHITE, fade), 1.5f));
                    }
                }
            }
        }
    }

    private void avatar(Canvas c, float x, float y, float r, int color) {
        c.drawCircle(x, y, r, F(color));
        rect.set(x - 1.7f * r, y + 1.4f * r, x + 1.7f * r, y + 1.4f * r + 3.2f * r);
        c.drawArc(rect, 180f, 180f, true, F(color));
    }

    private void drawPatients(Canvas c) {
        glow(c, 0, 0, 46);
        float b0 = 2.5f * (float) Math.sin(TWO_PI * t);
        float b1 = 3f * (float) Math.sin(TWO_PI * t + 2.1f);
        float b2 = 2.5f * (float) Math.sin(TWO_PI * t + 4.2f);
        avatar(c, -27f, -4f + b0, 7.5f, withAlpha(cInk, 0.55f));
        avatar(c, 27f, -4f + b2, 7.5f, withAlpha(cInk, 0.55f));
        avatar(c, 0f, -14f + b1, 10.5f, cAccent);
        float s = 1f + 0.14f * wave(frac(t * 2f));
        c.save();
        c.translate(24f, -30f);
        c.scale(s, s);
        c.drawCircle(0f, 0f, 8f, F(cGreen));
        Paint p = S(Color.WHITE, 2f);
        c.drawLine(-3.6f, 0f, 3.6f, 0f, p);
        c.drawLine(0f, -3.6f, 0f, 3.6f, p);
        c.restore();
    }

    private void sparkle(Canvas c, float x, float y, float s, int color) {
        path.reset();
        path.moveTo(x, y - s);
        path.quadTo(x, y, x + s, y);
        path.quadTo(x, y, x, y + s);
        path.quadTo(x, y, x - s, y);
        path.quadTo(x, y, x, y - s);
        path.close();
        c.drawPath(path, F(color));
    }

    private void drawAi(Canvas c) {
        glow(c, 0, 0, 46);
        rect.set(-28f, -24f, 28f, 14f);
        c.drawRoundRect(rect, 12f, 12f, F(cCard));
        c.drawRoundRect(rect, 12f, 12f, S(cLine, 1.4f));
        path.reset();
        path.moveTo(-14f, 13f);
        path.lineTo(-20f, 24f);
        path.lineTo(-4f, 13f);
        path.close();
        c.drawPath(path, F(cCard));
        for (int i = -1; i <= 1; i++) {
            float k = Math.max(0f, (float) Math.sin(TWO_PI * t * 2f - (i + 1) * 0.9f));
            c.drawCircle(i * 11f, -5f - 5f * k, 3.4f, F(mix(withAlpha(cInk, 0.5f), cAccent, k)));
        }
        for (int i = 0; i < 3; i++) {
            float ang = TWO_PI * (t + i / 3f);
            float x = (float) Math.cos(ang) * 40f;
            float y = (float) Math.sin(ang) * 26f - 6f;
            float sz = 3.5f + 3f * wave(frac(t * 3f + i / 3f));
            sparkle(c, x, y, sz, i == 1 ? cGold : cAccent);
        }
    }

    private void drawCloud(Canvas c) {
        glow(c, 0, 0, 46);
        for (int i = 0; i < 3; i++) {
            float p = frac(t * 1.5f + i / 3f);
            float x = -26f + i * 26f;
            float y = 36f - 70f * p;
            float al = (float) Math.sin(Math.PI * p);
            rect.set(x - 4f, y - 5f, x + 4f, y + 5f);
            c.drawRoundRect(rect, 1.6f, 1.6f, F(withAlpha(cInk, 0.5f * al)));
        }
        Paint f = F(cSoft);
        c.drawCircle(-15f, 2f, 12f, f);
        c.drawCircle(0f, -7f, 17f, f);
        c.drawCircle(17f, 2f, 12f, f);
        rect.set(-27f, 0f, 29f, 14f);
        c.drawRoundRect(rect, 7f, 7f, f);
        float lift = -2.5f * wave(frac(t * 2f));
        Paint p = S(cAccent, 3.2f);
        c.drawLine(0f, 10f + lift, 0f, -8f + lift, p);
        c.drawLine(-7f, -2f + lift, 0f, -9f + lift, p);
        c.drawLine(7f, -2f + lift, 0f, -9f + lift, p);
    }

    private void drawSearch(Canvas c) {
        glow(c, 0, 0, 46);
        for (int i = 0; i < 3; i++) {
            float w = 46f - i * 9f;
            float shimmer = wave(frac(t * 2f - i * 0.2f));
            rect.set(-w / 2f - 6f, 22f + i * 8f, w / 2f - 6f, 26f + i * 8f);
            c.drawRoundRect(rect, 2f, 2f, F(mix(withAlpha(cInk, 0.25f), withAlpha(cAccent, 0.8f), shimmer)));
        }
        float mx = 16f * (float) Math.cos(TWO_PI * t);
        float my = 8f * (float) Math.sin(TWO_PI * t * 2f) - 6f;
        float ring = frac(t * 2f);
        c.drawCircle(mx, my, 13f + 14f * ring, S(withAlpha(cAccent, 0.35f * (1f - ring)), 1.4f));
        c.drawCircle(mx, my, 13f, F(withAlpha(cCard, 0.9f)));
        c.drawCircle(mx, my, 13f, S(cAccent, 3.6f));
        c.drawLine(mx + 9.5f, my + 9.5f, mx + 21f, my + 21f, S(cAccent, 5f));
    }

    private void gear(Canvas c, float x, float y, float r, int teeth, float angle, int color) {
        c.save();
        c.rotate(angle, x, y);
        for (int i = 0; i < teeth; i++) {
            c.save();
            c.rotate(i * 360f / teeth, x, y);
            rect.set(x - r * 0.2f, y - r - r * 0.32f, x + r * 0.2f, y - r * 0.8f);
            c.drawRoundRect(rect, 1.6f, 1.6f, F(color));
            c.restore();
        }
        c.drawCircle(x, y, r, F(color));
        c.drawCircle(x, y, r * 0.42f, F(cCard));
        c.restore();
    }

    private void drawGears(Canvas c) {
        glow(c, 0, 0, 46);
        gear(c, -12f, 4f, 19f, 10, t * 360f, withAlpha(cInk, 0.6f));
        gear(c, 24f, -16f, 11f, 7, -t * 360f * 19f / 11f + 20f, cAccent);
        gear(c, 20f, 22f, 8.5f, 6, -t * 360f * 19f / 8.5f, cGold);
    }

    private void drawBook(Canvas c) {
        glow(c, 0, 0, 46);
        rect.set(-32f, -24f, -1f, 26f);
        c.drawRoundRect(rect, 3f, 3f, F(cCard));
        c.drawRoundRect(rect, 3f, 3f, S(cLine, 1.3f));
        rect.set(1f, -24f, 32f, 26f);
        c.drawRoundRect(rect, 3f, 3f, F(cCard));
        c.drawRoundRect(rect, 3f, 3f, S(cLine, 1.3f));
        Paint ln = S(withAlpha(cInk, 0.35f), 1.6f);
        for (int i = 0; i < 5; i++) {
            float y = -14f + i * 8f;
            c.drawLine(-26f, y, -8f, y, ln);
            c.drawLine(7f, y, 26f - (i % 2) * 6f, y, ln);
        }
        float f = ease(frac(t * 1.5f));
        float sx = (float) Math.cos(Math.PI * f);
        c.save();
        c.scale(sx, 1f, 0f, 0f);
        rect.set(0f, -24f, 32f, 26f);
        c.drawRoundRect(rect, 3f, 3f, F(mix(cCard, cSoft, 0.7f)));
        c.drawRoundRect(rect, 3f, 3f, S(cAccent, 1.4f));
        c.restore();
        rect.set(-1.4f, -24f, 1.4f, 26f);
        c.drawRect(rect, F(withAlpha(cInk, 0.35f)));
        path.reset();
        path.moveTo(18f, -24f);
        path.lineTo(18f, -12f);
        path.lineTo(22f, -16f);
        path.lineTo(26f, -12f);
        path.lineTo(26f, -24f);
        path.close();
        c.drawPath(path, F(cAccent));
    }

    private void starPath(float r) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            float rr = (i % 2 == 0) ? r : r * 0.46f;
            float a = (float) (-Math.PI / 2 + i * Math.PI / 5);
            float x = (float) Math.cos(a) * rr;
            float y = (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
    }

    private void drawStar(Canvas c) {
        glow(c, 0, 0, 46);
        float s = 1f + 0.09f * wave(frac(t * 2f));
        c.save();
        c.rotate(7f * (float) Math.sin(TWO_PI * t));
        c.scale(s, s);
        starPath(24f);
        c.drawPath(path, F(cGold));
        c.drawPath(path, S(cAccent, 2f));
        c.restore();
        float[][] pts = {{-36f, -16f}, {36f, -8f}, {26f, 26f}, {-28f, 24f}};
        for (int i = 0; i < pts.length; i++) {
            float k = wave(frac(t * 2f + i * 0.25f));
            sparkle(c, pts[i][0], pts[i][1], 2f + 4f * k, withAlpha(cAccent, 0.4f + 0.6f * k));
        }
    }

    private void drawDoc(Canvas c) {
        glow(c, 0, 0, 46);
        rect.set(-24f, -34f, 24f, 34f);
        c.drawRoundRect(rect, 5f, 5f, F(cCard));
        c.drawRoundRect(rect, 5f, 5f, S(cLine, 1.4f));
        int rows = 6;
        int hi = (int) Math.floor(frac(t * 1.5f) * rows);
        float local = frac(frac(t * 1.5f) * rows);
        for (int i = 0; i < rows; i++) {
            float y = -22f + i * 9f;
            float w = (i == 0) ? 22f : (i % 3 == 2 ? 28f : 36f);
            rect.set(-18f, y - 1.6f, -18f + w, y + 1.6f);
            c.drawRoundRect(rect, 1.6f, 1.6f, F(withAlpha(cInk, 0.3f)));
            if (i == hi) {
                rect.set(-20f, y - 4.4f, -20f + 8f + (w + 4f) * ease(local), y + 4.4f);
                c.drawRoundRect(rect, 2.4f, 2.4f, F(withAlpha(cAccent, 0.35f)));
            }
        }
        for (int i = 0; i < 3; i++) {
            float k = clamp(wave(frac(t * 2f - i * 0.18f)));
            rect.set(30f - (6f + i * 5f), -22f - (6f + i * 5f), 30f + (6f + i * 5f), -22f + (6f + i * 5f));
            c.drawArc(rect, -45f, 90f, false, S(withAlpha(cAccent, 0.25f + 0.75f * k), 2f));
        }
        c.drawCircle(30f, -22f, 2.2f, F(cAccent));
    }

    private void drawClipboard(Canvas c) {
        glow(c, 0, 0, 46);
        rect.set(-26f, -32f, 26f, 36f);
        c.drawRoundRect(rect, 6f, 6f, F(cCard));
        c.drawRoundRect(rect, 6f, 6f, S(cLine, 1.4f));
        rect.set(-10f, -38f, 10f, -27f);
        c.drawRoundRect(rect, 3.4f, 3.4f, F(cAccent));
        float p = t * 6.5f;
        float fade = p < 5.2f ? 1f : clamp(1f - (p - 5.2f) / 1.2f);
        for (int i = 0; i < 4; i++) {
            float y = -14f + i * 14f;
            c.drawCircle(-14f, y, 5f, S(withAlpha(cInk, 0.5f), 1.6f));
            rect.set(-5f, y - 1.8f, 18f - (i % 2) * 6f, y + 1.8f);
            c.drawRoundRect(rect, 1.8f, 1.8f, F(withAlpha(cInk, 0.28f)));
            float s = clamp(p - i);
            if (s > 0f && fade > 0f) {
                c.drawCircle(-14f, y, 5f * overshoot(s), F(withAlpha(cGreen, fade)));
                if (s > 0.6f) {
                    path.reset();
                    path.moveTo(-16.6f, y);
                    path.lineTo(-14.6f, y + 2.2f);
                    path.lineTo(-11.2f, y - 2.4f);
                    c.drawPath(path, S(withAlpha(Color.WHITE, fade), 1.7f));
                }
            }
        }
    }

    private void drawShield(Canvas c) {
        glow(c, 0, 0, 46);
        path.reset();
        path.moveTo(0f, -36f);
        path.lineTo(27f, -26f);
        path.quadTo(27f, 18f, 0f, 36f);
        path.quadTo(-27f, 18f, -27f, -26f);
        path.close();
        c.drawPath(path, F(cSoft));
        c.drawPath(path, S(cAccent, 2.2f));
        c.save();
        c.clipPath(path);
        c.translate(-60f + 120f * frac(t * 1.2f), 0f);
        c.rotate(20f);
        rect.set(-5f, -60f, 5f, 60f);
        c.drawRect(rect, F(withAlpha(Color.WHITE, 0.35f)));
        c.restore();
        float pulse = 1f + 0.05f * wave(frac(t * 2f));
        c.save();
        c.scale(pulse, pulse);
        rect.set(-9f, -2f, 9f, 16f);
        c.drawRoundRect(rect, 3f, 3f, F(cAccent));
        rect.set(-6f, -15f, 6f, 3f);
        c.drawArc(rect, 180f, 180f, false, S(cAccent, 2.8f));
        c.drawCircle(0f, 7f, 2f, F(Color.WHITE));
        c.restore();
    }
}
