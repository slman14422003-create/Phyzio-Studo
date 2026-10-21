package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;

public class AmbientView extends TickerView {

    private static final int N = 16;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] px = new float[N];
    private final float[] py = new float[N];
    private final float[] ps = new float[N];
    private final float[] pv = new float[N];
    private final float[] pp = new float[N];
    private final int accent;
    private final int gold;
    private Shader blobA;
    private Shader blobB;
    private float blobR;
    private float boost;
    private float boostTarget;

    public AmbientView(Context context) {
        this(context, null);
    }

    public AmbientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = context.getColor(R.color.claude_orange);
        gold = context.getColor(R.color.accent_gold);
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < N; i++) {
            px[i] = rnd.nextFloat();
            py[i] = rnd.nextFloat();
            ps[i] = 0.6f + rnd.nextFloat() * 1.6f;
            pv[i] = 0.012f + rnd.nextFloat() * 0.03f;
            pp[i] = rnd.nextFloat() * 6.28f;
        }
    }

    public void setBoost(boolean on) {
        boostTarget = on ? 1f : 0f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        blobR = Math.max(w, h) * 0.55f;
        blobA = new RadialGradient(0f, 0f, blobR, Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT, Shader.TileMode.CLAMP);
        blobB = new RadialGradient(0f, 0f, blobR * 0.8f, Color.argb(34, Color.red(gold), Color.green(gold), Color.blue(gold)),
                Color.TRANSPARENT, Shader.TileMode.CLAMP);
    }

    @Override
    protected void step(float dt) {
        boost += (boostTarget - boost) * Math.min(1f, dt * 3f);
        float k = 1f + boost * 3.5f;
        for (int i = 0; i < N; i++) {
            py[i] -= pv[i] * dt * k;
            if (py[i] < -0.05f) {
                py[i] = 1.05f;
            }
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();
        if (blobA == null) return;
        float sp = 0.25f + boost * 0.6f;
        paint.setStyle(Paint.Style.FILL);

        c.save();
        c.translate(w * (0.2f + 0.1f * (float) Math.sin(time * sp)), h * (0.25f + 0.08f * (float) Math.cos(time * sp * 0.8f)));
        paint.setShader(blobA);
        c.drawCircle(0f, 0f, blobR, paint);
        c.restore();

        c.save();
        c.translate(w * (0.8f + 0.1f * (float) Math.cos(time * sp * 0.7f)), h * (0.78f + 0.08f * (float) Math.sin(time * sp)));
        paint.setShader(blobB);
        c.drawCircle(0f, 0f, blobR * 0.8f, paint);
        c.restore();
        paint.setShader(null);

        for (int i = 0; i < N; i++) {
            float x = (px[i] + 0.02f * (float) Math.sin(time * 0.7f + pp[i])) * w;
            float y = py[i] * h;
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 1.8f + pp[i]);
            int col = i % 3 == 0 ? gold : accent;
            int a = Math.round((26 + 70 * tw) * (1f + boost * 0.8f));
            paint.setColor(Color.argb(Math.min(160, a), Color.red(col), Color.green(col), Color.blue(col)));
            float s = Ui.dp(getContext(), ps[i] * 1.6f);
            if (i % 2 == 0) {
                path.reset();
                path.moveTo(x, y - s * 2f);
                path.quadTo(x, y, x + s * 2f, y);
                path.quadTo(x, y, x, y + s * 2f);
                path.quadTo(x, y, x - s * 2f, y);
                path.quadTo(x, y, x, y - s * 2f);
                path.close();
                c.drawPath(path, paint);
            } else {
                c.drawCircle(x, y, s, paint);
            }
        }
    }
}
