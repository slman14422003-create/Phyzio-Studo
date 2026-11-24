package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SessionCalendarView extends View {

    public interface OnDayClickListener {
        void onDayClick(long dayStart);
    }

    private static final int COLS = 7;

    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final SparseArray<int[]> dayData = new SparseArray<>();
    private final String[] weekdays;
    private final String minutesFormat;
    private final float density;
    private final float headerHeight;
    private final float cellHeight;

    private int year;
    private int month;
    private int daysInMonth;
    private int firstCol;
    private int rows = 5;
    private int scheduleMask;
    private long selectedDay;
    private long todayStart;
    private OnDayClickListener listener;

    public SessionCalendarView(Context context) {
        this(context, null);
    }

    public SessionCalendarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        headerHeight = 30f * density;
        cellHeight = 54f * density;
        weekdays = context.getResources().getStringArray(R.array.cal_weekdays_short);
        minutesFormat = context.getString(R.string.cal_minutes_short);

        numberPaint.setTextAlign(Paint.Align.CENTER);
        numberPaint.setTextSize(15f * density);
        numberPaint.setFakeBoldText(true);
        smallPaint.setTextAlign(Paint.Align.CENTER);
        smallPaint.setTextSize(9.5f * density);
        smallPaint.setFakeBoldText(true);
        headerPaint.setTextAlign(Paint.Align.CENTER);
        headerPaint.setTextSize(12f * density);
        headerPaint.setFakeBoldText(true);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.5f * density);
        fillPaint.setStyle(Paint.Style.FILL);

        Calendar now = Calendar.getInstance();
        todayStart = Fmt.startOfDay(now.getTimeInMillis());
        setMonth(now.get(Calendar.YEAR), now.get(Calendar.MONTH));
        setClickable(true);
    }

    public void setOnDayClickListener(OnDayClickListener l) {
        listener = l;
    }

    public void setMonth(int y, int m) {
        year = y;
        month = m;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, 1);
        daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        firstCol = c.get(Calendar.DAY_OF_WEEK) % 7;
        rows = (int) Math.ceil((firstCol + daysInMonth) / (double) COLS);
        todayStart = Fmt.startOfDay(System.currentTimeMillis());
        requestLayout();
        invalidate();
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public void setSelectedDay(long dayStart) {
        selectedDay = dayStart;
        invalidate();
    }

    public void setData(List<Patient.Session> sessions, int sessionDaysMask) {
        scheduleMask = sessionDaysMask;
        dayData.clear();
        Calendar c = Calendar.getInstance();
        for (Patient.Session s : sessions) {
            c.setTimeInMillis(s.date);
            int key = keyOf(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            int[] d = dayData.get(key);
            if (d == null) {
                d = new int[2];
                dayData.put(key, d);
            }
            d[0]++;
            d[1] += Math.max(0, s.durationMin);
        }
        invalidate();
    }

    private static int keyOf(int y, int m, int d) {
        return y * 10000 + m * 100 + d;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = (int) (headerHeight + cellHeight * rows);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float cellW = w / COLS;

        headerPaint.setColor(getContext().getColor(R.color.text_tertiary));
        for (int col = 0; col < COLS; col++) {
            float cx = w - (col + 0.5f) * cellW;
            canvas.drawText(weekdays[col], cx, headerHeight * 0.68f, headerPaint);
        }

        int primary = getContext().getColor(R.color.primary_cyan);
        int primarySoft = getContext().getColor(R.color.primary_soft);
        int textPrimary = getContext().getColor(R.color.text_primary);
        int textTertiary = getContext().getColor(R.color.text_tertiary);
        int white = getContext().getColor(R.color.white);
        float pad = 3f * density;
        float radius = 14f * density;
        Calendar c = Calendar.getInstance();

        for (int day = 1; day <= daysInMonth; day++) {
            int idx = firstCol + day - 1;
            int row = idx / COLS;
            int col = idx % COLS;
            float left = w - (col + 1) * cellW;
            float top = headerHeight + row * cellHeight;
            rect.set(left + pad, top + pad, left + cellW - pad, top + cellHeight - pad);

            c.clear();
            c.set(year, month, day);
            long dayStart = c.getTimeInMillis();
            int dow = c.get(Calendar.DAY_OF_WEEK);
            int[] data = dayData.get(keyOf(year, month, day));
            boolean hasSession = data != null;
            boolean scheduled = !hasSession && dayStart >= todayStart && (scheduleMask & (1 << (dow - 1))) != 0;
            boolean selected = dayStart == selectedDay;
            boolean today = dayStart == todayStart;

            int numberColor = textPrimary;
            int smallColor = primary;

            if (selected) {
                fillPaint.setColor(primary);
                canvas.drawRoundRect(rect, radius, radius, fillPaint);
                numberColor = white;
                smallColor = white;
            } else if (hasSession) {
                fillPaint.setColor(primarySoft);
                canvas.drawRoundRect(rect, radius, radius, fillPaint);
            } else if (scheduled) {
                strokePaint.setColor(primary);
                strokePaint.setPathEffect(new DashPathEffect(new float[]{4f * density, 3f * density}, 0));
                canvas.drawRoundRect(rect, radius, radius, strokePaint);
                strokePaint.setPathEffect(null);
            } else if (dayStart < todayStart) {
                numberColor = textTertiary;
            }

            if (today && !selected) {
                strokePaint.setColor(primary);
                strokePaint.setPathEffect(null);
                canvas.drawRoundRect(rect, radius, radius, strokePaint);
            }

            float cx = rect.centerX();
            numberPaint.setColor(numberColor);
            float numberY = hasSession ? rect.top + rect.height() * 0.50f : rect.centerY() + numberPaint.getTextSize() * 0.35f;
            canvas.drawText(String.format(Locale.US, "%d", day), cx, numberY, numberPaint);

            if (hasSession) {
                smallPaint.setColor(smallColor);
                String label = data[1] > 0
                        ? String.format(Locale.US, minutesFormat, data[1])
                        : String.format(Locale.US, "x%d", data[0]);
                canvas.drawText(label, cx, rect.bottom - 7f * density, smallPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float w = getWidth();
            float cellW = w / COLS;
            float y = e.getY() - headerHeight;
            if (y >= 0) {
                int row = (int) (y / cellHeight);
                int col = (int) ((w - e.getX()) / cellW);
                if (col >= 0 && col < COLS && row >= 0 && row < rows) {
                    int day = row * COLS + col - firstCol + 1;
                    if (day >= 1 && day <= daysInMonth) {
                        Calendar c = Calendar.getInstance();
                        c.clear();
                        c.set(year, month, day);
                        performClick();
                        if (listener != null) listener.onDayClick(c.getTimeInMillis());
                    }
                }
            }
            return true;
        }
        return e.getAction() == MotionEvent.ACTION_DOWN || super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
