package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class FlowLayout extends ViewGroup {

    private int hGap;
    private int vGap;

    public FlowLayout(Context context) {
        this(context, null);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        hGap = Ui.dp(context, 8);
        vGap = Ui.dp(context, 8);
    }

    public void setGaps(int horizontalDp, int verticalDp) {
        hGap = Ui.dp(getContext(), horizontalDp);
        vGap = Ui.dp(getContext(), verticalDp);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int total = MeasureSpec.getSize(widthSpec);
        int maxW = total - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = 0;
        int rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int cw = c.getMeasuredWidth();
            int ch = c.getMeasuredHeight();
            if (x > 0 && x + cw > maxW) {
                x = 0;
                y += rowH + vGap;
                rowH = 0;
            }
            x += cw + hGap;
            rowH = Math.max(rowH, ch);
        }
        int h = y + rowH + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(total, resolveSize(h, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int maxW = width - getPaddingLeft() - getPaddingRight();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int x = 0;
        int y = getPaddingTop();
        int rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            int cw = c.getMeasuredWidth();
            int ch = c.getMeasuredHeight();
            if (x > 0 && x + cw > maxW) {
                x = 0;
                y += rowH + vGap;
                rowH = 0;
            }
            int left = rtl ? width - getPaddingRight() - x - cw : getPaddingLeft() + x;
            c.layout(left, y, left + cw, y + ch);
            x += cw + hGap;
            rowH = Math.max(rowH, ch);
        }
    }
}
