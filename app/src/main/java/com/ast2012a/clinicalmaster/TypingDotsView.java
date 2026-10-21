package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

public class TypingDotsView extends TickerView {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TypingDotsView(Context context) {
        this(context, null);
    }

    public TypingDotsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(context.getColor(R.color.claude_orange));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 34), widthSpec),
                resolveSize(Ui.dp(getContext(), 20), heightSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();
        float r = Ui.dp(getContext(), 3f);
        float gap = (w - 6f * r) / 2f;
        float amp = Ui.dp(getContext(), 5f);
        for (int i = 0; i < 3; i++) {
            float ph = time * 3.2f - i * 0.55f;
            float lift = Math.max(0f, (float) Math.sin(ph));
            float x = r + i * (2f * r + gap);
            paint.setAlpha(Math.round(110 + 145 * lift));
            c.drawCircle(x, h / 2f - amp * lift, r * (1f + 0.25f * lift), paint);
        }
    }
}
