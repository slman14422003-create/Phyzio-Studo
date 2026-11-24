package com.ast2012a.clinicalmaster;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * علامة التطبيق المتحركة (نفس تصميم أيقونة التطبيق تمامًا): شارة داكنة دافئة،
 * خط نبضة برتقالي يُرسم تدريجيًا، ثم نجمة Semo AI تظهر بقفزة مع موجة ضوء.
 * بعد اكتمال الظهور تبقى حيّة: النجمة تدور ببطء والنبضة تتنفس.
 * إحداثيات الرسم بنفس فضاء 108×108 المستخدم في ic_launcher.
 */
public class SplashMarkView extends View {

    private static final float V = 108f;
    private static final float STAR_X = 72f, STAR_Y = 36f;

    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF(6, 6, 102, 102);

    private final Path pulse = new Path();
    private final Path seg = new Path();
    private final PathMeasure pm;
    private final float len;
    private final float[] pos = new float[2];

    private final OvershootInterpolator overshoot = new OvershootInterpolator(1.7f);
    private final DecelerateInterpolator decel = new DecelerateInterpolator(1.6f);

    private float t = 0f;     // تقدّم الظهور 0..1
    private float idle = 0f;  // دورة الحركة الحيّة 0..1
    private ValueAnimator reveal, idleAnim;
    private Runnable onRevealEnd;

    public SplashMarkView(Context c) {
        this(c, null);
    }

    public SplashMarkView(Context c, AttributeSet a) {
        super(c, a);
        pulse.moveTo(25, 57);
        pulse.lineTo(38, 57);
        pulse.lineTo(45, 41);
        pulse.lineTo(55, 73);
        pulse.lineTo(62, 50);
        pulse.lineTo(67, 57);
        pulse.lineTo(83, 57);
        pm = new PathMeasure(pulse, false);
        len = pm.getLength();

        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.2f);
        rim.setColor(0x66E58A68);

        for (Paint p : new Paint[]{lineGlow, line, star, ring}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        lineGlow.setStrokeWidth(11f);
        lineGlow.setColor(0x33D97757);
        line.setStrokeWidth(6.4f);
        star.setStrokeWidth(3.2f);
        star.setColor(0xFFFFE6D8);
        ring.setStrokeWidth(1.8f);
        ring.setColor(0xFFF0A27F);
        head.setColor(0xFFFFE6D8);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        badge.setShader(new LinearGradient(6, 6, 102, 102, 0xFF3B2318, 0xFF150E0B, Shader.TileMode.CLAMP));
        line.setShader(new LinearGradient(25, 0, 83, 0, 0xFFF4A582, 0xFFD97757, Shader.TileMode.CLAMP));
        halo.setShader(new RadialGradient(54, 54, 52, 0x55D97757, 0x00D97757, Shader.TileMode.CLAMP));
    }

    public void setOnRevealEnd(Runnable r) {
        onRevealEnd = r;
    }

    /** يبدأ حركة الظهور ثم الحركة الحيّة. */
    public void play(long durationMs) {
        stop();
        t = 0f;
        reveal = ValueAnimator.ofFloat(0f, 1f);
        reveal.setDuration(durationMs);
        reveal.setInterpolator(new LinearInterpolator());
        reveal.addUpdateListener(a -> {
            t = (Float) a.getAnimatedValue();
            invalidate();
        });
        reveal.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                t = 1f;
                invalidate();
                if (cancelled) return;
                startIdle();
                if (onRevealEnd != null) onRevealEnd.run();
            }
        });
        reveal.start();
    }

    private void startIdle() {
        idleAnim = ValueAnimator.ofFloat(0f, 1f);
        idleAnim.setDuration(6000);
        idleAnim.setRepeatCount(ValueAnimator.INFINITE);
        idleAnim.setInterpolator(new LinearInterpolator());
        idleAnim.addUpdateListener(a -> {
            idle = (Float) a.getAnimatedValue();
            invalidate();
        });
        idleAnim.start();
    }

    public void stop() {
        if (reveal != null) reveal.cancel();
        if (idleAnim != null) idleAnim.cancel();
        reveal = null;
        idleAnim = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / V;
        c.save();
        c.scale(s, s);

        float breathe = 1f + 0.012f * (float) Math.sin(idle * Math.PI * 2);

        // هالة خلف الشارة
        float haloP = clamp(t / 0.35f);
        halo.setAlpha((int) (255 * haloP));
        c.drawCircle(54, 54, 52, halo);

        // الشارة: قفزة بتجاوز بسيط
        float bp = clamp(t / 0.28f);
        float bs = (0.55f + 0.45f * overshoot.getInterpolation(bp)) * breathe;
        badge.setAlpha((int) (255 * clamp(bp * 1.6f)));
        c.save();
        c.scale(bs, bs, 54, 54);
        c.drawRoundRect(rect, 28, 28, badge);
        rim.setAlpha((int) (102 * clamp(bp * 1.6f)));
        c.drawRoundRect(rect, 28, 28, rim);

        // خط النبضة يُرسم تدريجيًا
        float lp = decel.getInterpolation(clamp((t - 0.2f) / 0.42f));
        if (lp > 0f) {
            seg.reset();
            pm.getSegment(0, len * lp, seg, true);
            float glowPulse = 0.75f + 0.25f * (float) Math.sin(idle * Math.PI * 4);
            lineGlow.setAlpha((int) (0x40 * (t >= 1f ? glowPulse : 1f)));
            c.drawPath(seg, lineGlow);
            c.drawPath(seg, line);
            if (lp < 1f) {
                pm.getPosTan(len * lp, pos, null);
                head.setAlpha(255);
                c.drawCircle(pos[0], pos[1], 3.4f, head);
            }
        }

        // النجمة: تظهر بقفزة ودوران
        float sp = clamp((t - 0.58f) / 0.22f);
        if (sp > 0f) {
            float ss = overshoot.getInterpolation(sp);
            float rot = (1f - sp) * -120f + idle * 360f;
            c.save();
            c.translate(STAR_X, STAR_Y);
            c.rotate(rot);
            c.scale(ss, ss);
            c.drawLine(0, -8, 0, 8, star);
            c.drawLine(-8, 0, 8, 0, star);
            c.drawLine(-5f, -5f, 5f, 5f, star);
            c.drawLine(5f, -5f, -5f, 5f, star);
            c.restore();
        }

        // موجة ضوء تنطلق من النجمة
        float rp = clamp((t - 0.62f) / 0.33f);
        if (rp > 0f && rp < 1f) {
            ring.setAlpha((int) (150 * (1f - rp)));
            c.drawCircle(STAR_X, STAR_Y, 6f + 34f * decel.getInterpolation(rp), ring);
        }
        c.restore();
        c.restore();
    }
}
