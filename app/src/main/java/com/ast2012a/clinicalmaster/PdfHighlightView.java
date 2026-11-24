package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * طبقة شفافة فوق صورة صفحة الـ PDF تظلّل نتائج البحث داخل الملف (أصفر لكل النتائج،
 * وبرتقالي للنتيجة الحالية). كل الإحداثيات نسب (0..1) من أبعاد الصفحة.
 */
public class PdfHighlightView extends View {

    private final Paint searchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint searchCurrentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> searchRects = new ArrayList<>();
    private final List<RectF> searchCurrentRects = new ArrayList<>();
    private final Paint readingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> readingRects = new ArrayList<>();
    private final RectF tmp = new RectF();

    public PdfHighlightView(Context context) {
        this(context, null);
    }

    public PdfHighlightView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        searchPaint.setStyle(Paint.Style.FILL);
        searchPaint.setColor(0x66FFD54F);   // أصفر شفاف لكل نتائج البحث
        searchCurrentPaint.setStyle(Paint.Style.FILL);
        searchCurrentPaint.setColor(0xB3FF8A3D); // برتقالي واضح للنتيجة الحالية
        readingPaint.setStyle(Paint.Style.FILL);
        readingPaint.setColor(0x802E9BFF);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Boxes of the sentence being read aloud on this page (ratios 0..1), or null to clear. */
    public void setReadingRects(@Nullable List<RectF> rects) {
        boolean had = !readingRects.isEmpty();
        readingRects.clear();
        if (rects != null) readingRects.addAll(rects);
        if (had || !readingRects.isEmpty()) invalidate();
    }

    /** نتائج البحث في هذه الصفحة: others = باقي النتائج، current = النتيجة المحددة الآن. */
    public void setSearchHits(@Nullable List<RectF> others, @Nullable List<RectF> current) {
        boolean had = !searchRects.isEmpty() || !searchCurrentRects.isEmpty();
        searchRects.clear();
        searchCurrentRects.clear();
        if (others != null) searchRects.addAll(others);
        if (current != null) searchCurrentRects.addAll(current);
        if (had || !searchRects.isEmpty() || !searchCurrentRects.isEmpty()) invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float pad = Math.max(1.5f, w * 0.002f);
        for (RectF r : searchRects) {
            tmp.set(r.left * w - pad, r.top * h - pad, r.right * w + pad, r.bottom * h + pad);
            canvas.drawRoundRect(tmp, 4f, 4f, searchPaint);
        }
        for (RectF r : searchCurrentRects) {
            tmp.set(r.left * w - pad, r.top * h - pad, r.right * w + pad, r.bottom * h + pad);
            canvas.drawRoundRect(tmp, 4f, 4f, searchCurrentPaint);
        }
        for (RectF r : readingRects) {
            tmp.set(r.left * w - pad, r.top * h - pad, r.right * w + pad, r.bottom * h + pad);
            canvas.drawRoundRect(tmp, 4f, 4f, readingPaint);
        }
    }
}
