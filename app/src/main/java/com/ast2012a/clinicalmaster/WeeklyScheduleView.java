package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** شبكة أسبوعية: الأيام أعمدة (السبت من اليمين) والساعات صفوف، وكل جلسة مربع في خانتها. */
public class WeeklyScheduleView extends View {

    public static final class Entry {
        final Patient patient;
        final int dow;
        final int startMin;
        final String label;
        final String time;
        final int color;
        int lane;
        int lanes = 1;

        Entry(Patient patient, int dow, int startMin, String label, String time, int color) {
            this.patient = patient;
            this.dow = dow;
            this.startMin = startMin;
            this.label = label;
            this.time = time;
            this.color = color;
        }
    }

    public interface OnEntryClick {
        void onClick(Patient p);
    }

    private static final int DURATION_MIN = 60;

    private final List<Entry> entries = new ArrayList<>();
    private final List<RectF> rects = new ArrayList<>();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint dayText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hourText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint nameText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timeText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final String[] dayLabels;
    private final String am;
    private final String pm;
    private final float dp;
    private final float hourH;
    private final float headerH;
    private final float labelW;

    private int firstHour = 8;
    private int lastHour = 14;
    private int todayDow = Calendar.SUNDAY;
    private OnEntryClick listener;
    private final GestureDetector tap;

    public WeeklyScheduleView(Context c) {
        this(c, null);
    }

    public WeeklyScheduleView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        hourH = 68 * dp;
        headerH = 38 * dp;
        labelW = 34 * dp;
        dayLabels = c.getResources().getStringArray(R.array.pt_sched_days_short);
        am = c.getString(R.string.pt_sched_am);
        pm = c.getString(R.string.pt_sched_pm);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, dp * 0.6f));
        line.setColor(ContextCompat.getColor(c, R.color.glass_border));
        dayText.setTextSize(11.5f * dp);
        dayText.setFakeBoldText(true);
        dayText.setTextAlign(Paint.Align.CENTER);
        hourText.setTextSize(11f * dp);
        hourText.setTextAlign(Paint.Align.CENTER);
        hourText.setColor(ContextCompat.getColor(c, R.color.text_tertiary));
        nameText.setTextSize(10.5f * dp);
        nameText.setFakeBoldText(true);
        nameText.setColor(ContextCompat.getColor(c, R.color.text_primary));
        timeText.setTextSize(9.5f * dp);
        timeText.setColor(ContextCompat.getColor(c, R.color.text_secondary));
        tap = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                for (int i = rects.size() - 1; i >= 0; i--) {
                    if (rects.get(i).contains(e.getX(), e.getY())) {
                        performClick();
                        if (listener != null) listener.onClick(entries.get(i).patient);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public void setOnEntryClick(OnEntryClick l) {
        listener = l;
    }

    public void setPatients(List<Patient> patients) {
        entries.clear();
        int[] palette = {R.color.primary_cyan, R.color.accent_green, R.color.accent_gold,
                R.color.accent_violet, R.color.accent_red};
        int minH = 24;
        int maxH = 0;
        for (Patient p : patients) {
            if (!p.hasSchedule() || p.sessionTimeMin < 0) continue;
            int color = ContextCompat.getColor(getContext(),
                    palette[(p.id == null ? 0 : (p.id.hashCode() & 0x7fffffff)) % palette.length]);
            String label = p.displayName();
            String time = Fmt.minutesToTime(p.sessionTimeMin);
            for (int dow : Patient.WEEK_ORDER) {
                if (p.hasSessionDay(dow)) entries.add(new Entry(p, dow, p.sessionTimeMin, label, time, color));
            }
            minH = Math.min(minH, p.sessionTimeMin / 60);
            maxH = Math.max(maxH, (p.sessionTimeMin + DURATION_MIN + 59) / 60);
        }
        if (entries.isEmpty()) {
            firstHour = 8;
            lastHour = 14;
        } else {
            firstHour = Math.max(0, minH - 1);
            lastHour = Math.min(24, maxH + 1);
            if (lastHour - firstHour < 6) lastHour = Math.min(24, firstHour + 6);
            if (lastHour - firstHour < 6) firstHour = Math.max(0, lastHour - 6);
        }
        todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        assignLanes();
        requestLayout();
        invalidate();
    }

    private void assignLanes() {
        for (int dow : Patient.WEEK_ORDER) {
            List<Entry> day = new ArrayList<>();
            for (Entry e : entries) if (e.dow == dow) day.add(e);
            Collections.sort(day, (a, b) -> Integer.compare(a.startMin, b.startMin));
            List<Entry> cluster = new ArrayList<>();
            int clusterEnd = -1;
            for (Entry e : day) {
                if (!cluster.isEmpty() && e.startMin >= clusterEnd) {
                    closeCluster(cluster);
                    cluster = new ArrayList<>();
                }
                int lane = 0;
                boolean free;
                do {
                    free = true;
                    for (Entry o : cluster) {
                        if (o.lane == lane && o.startMin + DURATION_MIN > e.startMin) {
                            free = false;
                            lane++;
                            break;
                        }
                    }
                } while (!free);
                e.lane = lane;
                cluster.add(e);
                clusterEnd = Math.max(clusterEnd, e.startMin + DURATION_MIN);
            }
            closeCluster(cluster);
        }
    }

    private static void closeCluster(List<Entry> cluster) {
        int lanes = 1;
        for (Entry e : cluster) lanes = Math.max(lanes, e.lane + 1);
        for (Entry e : cluster) e.lanes = lanes;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int h = (int) (headerH + (lastHour - firstHour) * hourH);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), wSpec), h);
    }

    private String hourLabel(int h24) {
        int h = h24 % 12;
        if (h == 0) h = 12;
        return String.format(Locale.US, "%d", h) + " " + (h24 % 24 < 12 ? am : pm);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float gridW = w - labelW;
        float colW = gridW / 7f;
        int hours = lastHour - firstHour;
        float bottom = headerH + hours * hourH;

        int todayCol = indexOfDow(todayDow);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(ContextCompat.getColor(getContext(), R.color.glass_highlight));
        float tl = colLeft(todayCol, colW);
        canvas.drawRect(tl, 0, tl + colW, bottom, fill);

        for (int i = 0; i < 7; i++) {
            boolean today = i == todayCol;
            dayText.setColor(ContextCompat.getColor(getContext(),
                    today ? R.color.primary_cyan_dark : R.color.text_secondary));
            float cx = colLeft(i, colW) + colW / 2f;
            canvas.drawText(dayLabels[i], cx, headerH / 2f + dayText.getTextSize() / 3f, dayText);
        }
        for (int i = 0; i <= 7; i++) {
            float x = i * colW;
            canvas.drawLine(x, headerH, x, bottom, line);
        }
        for (int r = 0; r <= hours; r++) {
            float y = headerH + r * hourH;
            canvas.drawLine(0, y, gridW, y, line);
            if (r < hours) {
                canvas.drawText(hourLabel(firstHour + r), gridW + labelW / 2f, y + hourText.getTextSize() + 4 * dp, hourText);
            }
        }

        rects.clear();
        float pad = 2 * dp;
        for (Entry e : entries) {
            int col = indexOfDow(e.dow);
            float laneW = colW / e.lanes;
            float left = colLeft(col, colW) + laneW * e.lane + pad;
            float right = colLeft(col, colW) + laneW * (e.lane + 1) - pad;
            float top = headerH + (e.startMin - firstHour * 60) / 60f * hourH + pad;
            float bot = top + DURATION_MIN / 60f * hourH - 2 * pad;
            RectF r = new RectF(left, top, right, bot);
            rects.add(r);
            fill.setColor((e.color & 0x00FFFFFF) | 0x33000000);
            canvas.drawRoundRect(r, 6 * dp, 6 * dp, fill);
            fill.setColor(e.color);
            canvas.drawRoundRect(new RectF(r.right - 3 * dp, r.top + 4 * dp, r.right - 1 * dp, r.bottom - 4 * dp),
                    2 * dp, 2 * dp, fill);
            drawText(canvas, e, r);
        }
    }

    private void drawText(Canvas canvas, Entry e, RectF r) {
        int tw = (int) (r.width() - 8 * dp);
        if (tw <= 0) return;
        StaticLayout time = build(e.time, timeText, tw, 1);
        float avail = r.height() - 6 * dp - time.getHeight();
        int lineH = Math.max(1, nameText.getFontMetricsInt(null));
        int maxLines = Math.max(1, (int) (avail / lineH));
        StaticLayout name = build(e.label, nameText, tw, maxLines);
        canvas.save();
        canvas.translate(r.left + 3 * dp, r.top + 3 * dp);
        name.draw(canvas);
        canvas.translate(0, name.getHeight());
        time.draw(canvas);
        canvas.restore();
    }

    private static StaticLayout build(String text, TextPaint paint, int width, int maxLines) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .build();
    }

    /** السبت = العمود 0 من اليمين. */
    private static int indexOfDow(int dow) {
        for (int i = 0; i < Patient.WEEK_ORDER.length; i++) if (Patient.WEEK_ORDER[i] == dow) return i;
        return 0;
    }

    private float colLeft(int col, float colW) {
        return (6 - col) * colW;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return tap.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
