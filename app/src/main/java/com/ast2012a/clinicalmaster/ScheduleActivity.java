package com.ast2012a.clinicalmaster;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** شاشة جدول الجلسات: شريط أيام الأسبوع + أجندة اليوم المختار. */
public class ScheduleActivity extends AppCompatActivity {

    private static final int[] PALETTE = {
            R.color.primary_cyan, R.color.accent_green, R.color.accent_gold,
            R.color.accent_violet, R.color.accent_red
    };

    private static final class Item {
        final Patient patient;
        final int minutes; // -1 = بدون ساعة

        Item(Patient patient, int minutes) {
            this.patient = patient;
            this.minutes = minutes;
        }
    }

    private LinearLayout dayStrip;
    private LinearLayout agenda;
    private TextView dayTitle;
    private TextView dayCount;
    private TextView subtitle;
    private LayoutInflater inflater;

    private List<Patient> patients = new ArrayList<>();
    private final long[] dayStart = new long[7];
    private final List<List<Item>> week = new ArrayList<>();
    private int todayIdx;
    private int selected;
    private boolean selectionInitialized;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);
        inflater = LayoutInflater.from(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        dayStrip = findViewById(R.id.day_strip);
        agenda = findViewById(R.id.agenda_container);
        dayTitle = findViewById(R.id.day_title);
        dayCount = findViewById(R.id.day_count);
        subtitle = findViewById(R.id.header_subtitle);
    }

    @Override
    protected void onResume() {
        super.onResume();
        patients = PatientManager.loadPatients(this);
        buildWeek();
        if (!selectionInitialized) {
            selected = todayIdx;
            selectionInitialized = true;
        }
        renderStrip();
        renderDay();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ------------------------------------------------------------------
    // البيانات
    // ------------------------------------------------------------------

    private void buildWeek() {
        long now = System.currentTimeMillis();
        long todayStart = Fmt.startOfDay(now);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(todayStart);
        int todayDow = c.get(Calendar.DAY_OF_WEEK);
        todayIdx = 0;
        for (int i = 0; i < Patient.WEEK_ORDER.length; i++) {
            if (Patient.WEEK_ORDER[i] == todayDow) todayIdx = i;
        }
        c.add(Calendar.DAY_OF_YEAR, -todayIdx);
        for (int i = 0; i < 7; i++) {
            dayStart[i] = c.getTimeInMillis();
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        long weekEnd = c.getTimeInMillis();

        week.clear();
        for (int i = 0; i < 7; i++) {
            int dow = Patient.WEEK_ORDER[i];
            long from = dayStart[i];
            long to = i < 6 ? dayStart[i + 1] : weekEnd;
            List<Item> items = new ArrayList<>();
            for (Patient p : patients) {
                if (p.hasSessionDay(dow)) {
                    items.add(new Item(p, p.sessionTimeMin));
                } else if (p.nextAppointment >= todayStart && p.nextAppointment >= from
                        && p.nextAppointment < to) {
                    Calendar t = Calendar.getInstance();
                    t.setTimeInMillis(p.nextAppointment);
                    items.add(new Item(p, t.get(Calendar.HOUR_OF_DAY) * 60 + t.get(Calendar.MINUTE)));
                }
            }
            Collections.sort(items, (a, b) -> {
                if (a.minutes < 0 && b.minutes < 0) return 0;
                if (a.minutes < 0) return 1;
                if (b.minutes < 0) return -1;
                return Integer.compare(a.minutes, b.minutes);
            });
            week.add(items);
        }
        subtitle.setText(Fmt.date(dayStart[0]).substring(0, 5) + " – " + Fmt.date(dayStart[6]).substring(0, 5));
    }

    // ------------------------------------------------------------------
    // شريط الأيام
    // ------------------------------------------------------------------

    private void renderStrip() {
        dayStrip.removeAllViews();
        String[] shortNames = getResources().getStringArray(R.array.pt_sched_days_short);
        Calendar c = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            boolean sel = i == selected;
            boolean today = i == todayIdx;
            int count = week.get(i).size();

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 8));
            cell.setBackgroundResource(sel ? R.drawable.bg_day_cell_selected
                    : today ? R.drawable.bg_day_cell_today : R.drawable.bg_day_cell);
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setOnClickListener(v -> {
                selected = idx;
                renderStrip();
                renderDay();
            });
            dayStrip.addView(cell, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            int main = getColor(sel ? R.color.white : R.color.text_primary);
            int muted = sel ? Color.parseColor("#CCFFFFFF") : getColor(R.color.text_tertiary);

            TextView name = new TextView(this);
            name.setText(shortNames[i]);
            name.setTextSize(11.5f);
            name.setTextColor(muted);
            name.setGravity(Gravity.CENTER);
            cell.addView(name);

            c.setTimeInMillis(dayStart[i]);
            TextView num = new TextView(this);
            num.setText(String.valueOf(c.get(Calendar.DAY_OF_MONTH)));
            num.setTextSize(18f);
            num.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD);
            num.setTextColor(today && !sel ? getColor(R.color.primary_cyan_dark) : main);
            num.setTextDirection(View.TEXT_DIRECTION_LTR);
            num.setGravity(Gravity.CENTER);
            num.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 4));
            cell.addView(num);

            TextView dot = new TextView(this);
            dot.setGravity(Gravity.CENTER);
            dot.setTextSize(10.5f);
            dot.setTypeface(null, android.graphics.Typeface.BOLD);
            dot.setTextDirection(View.TEXT_DIRECTION_LTR);
            int size = Ui.dp(this, 18);
            if (count > 0) {
                dot.setText(String.valueOf(count));
                dot.setBackgroundResource(sel ? R.drawable.bg_count_dot_light : R.drawable.bg_count_dot);
                dot.setTextColor(sel ? getColor(R.color.primary_cyan_dark) : getColor(R.color.white));
            } else {
                dot.setText("");
            }
            cell.addView(dot, new LinearLayout.LayoutParams(size, size));
        }
    }

    // ------------------------------------------------------------------
    // أجندة اليوم
    // ------------------------------------------------------------------

    private static String sessionsWord(int n) {
        if (n == 0) return "لا جلسات";
        if (n == 1) return "جلسة واحدة";
        if (n == 2) return "جلستان";
        if (n <= 10) return n + " جلسات";
        return n + " جلسة";
    }

    private void renderDay() {
        List<Item> items = week.get(selected);
        String name = Fmt.dayName(Patient.WEEK_ORDER[selected]);
        String date = Fmt.date(dayStart[selected]).substring(0, 5);
        dayTitle.setText(selected == todayIdx ? "اليوم · " + name + " " + date : name + " " + date);
        dayCount.setText(sessionsWord(items.size()));

        agenda.removeAllViews();
        if (items.isEmpty()) {
            agenda.addView(buildEmpty());
            return;
        }

        Calendar nowC = Calendar.getInstance();
        int nowMin = nowC.get(Calendar.HOUR_OF_DAY) * 60 + nowC.get(Calendar.MINUTE);
        boolean isToday = selected == todayIdx;
        boolean noTimeHeader = false;
        for (Item it : items) {
            if (it.minutes < 0 && !noTimeHeader) {
                noTimeHeader = true;
                View h = inflater.inflate(R.layout.item_patient_section, agenda, false);
                ((TextView) h.findViewById(R.id.section_title)).setText("بدون ساعة محددة");
                h.findViewById(R.id.section_count).setVisibility(View.GONE);
                agenda.addView(h);
            }
            agenda.addView(buildEntry(it, isToday && it.minutes >= 0 && it.minutes + 60 < nowMin));
        }
        agenda.setLayoutAnimation(android.view.animation.AnimationUtils
                .loadLayoutAnimation(this, R.anim.layout_fall_stagger));
        agenda.scheduleLayoutAnimation();
    }

    private View buildEmpty() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, Ui.dp(this, 56), 0, 0);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_calendar);
        icon.setColorFilter(getColor(R.color.text_tertiary));
        box.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        TextView t = new TextView(this);
        t.setText("لا توجد جلسات في هذا اليوم");
        t.setTextColor(getColor(R.color.text_secondary));
        t.setTextSize(14.5f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 12), 0, 0);
        box.addView(t);
        return box;
    }

    private View buildEntry(final Item it, boolean past) {
        final Patient p = it.patient;
        View row = inflater.inflate(R.layout.item_agenda_entry, agenda, false);

        TextView time = row.findViewById(R.id.agenda_time);
        TextView ampm = row.findViewById(R.id.agenda_ampm);
        if (it.minutes >= 0) {
            String[] parts = Fmt.minutesToTime(it.minutes).split(" ");
            time.setText(parts[0]);
            ampm.setText(parts.length > 1 ? parts[1] : "");
        } else {
            time.setText("—");
            ampm.setText("");
        }

        int color = ContextCompat.getColor(this,
                PALETTE[Math.abs(p.id == null ? 0 : p.id.hashCode()) % PALETTE.length]);
        GradientDrawable bar = new GradientDrawable();
        bar.setCornerRadius(Ui.dp(this, 4));
        bar.setColor(color);
        row.findViewById(R.id.agenda_bar).setBackground(bar);

        ((TextView) row.findViewById(R.id.agenda_name)).setText(BidiText.fix(p.displayName()));
        TextView sub = row.findViewById(R.id.agenda_sub);
        String s = p.diagnosis == null ? "" : p.diagnosis.trim().replace('\n', ' ');
        if (s.isEmpty()) {
            sub.setVisibility(View.GONE);
        } else {
            sub.setText(BidiText.fix(s));
        }

        View call = row.findViewById(R.id.agenda_call);
        if (p.phone != null && !p.phone.trim().isEmpty()) {
            call.setVisibility(View.VISIBLE);
            call.setOnClickListener(v -> dial(p.phone));
        }

        View card = row.findViewById(R.id.agenda_card);
        card.setOnClickListener(v -> {
            if (Ui.isDoubleClick()) return;
            Intent i = new Intent(this, PatientDetailActivity.class);
            i.putExtra("patient_id", p.id);
            startActivity(i);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        Ui.applyPressFeedback(card);
        if (past) row.setAlpha(0.55f);
        return row;
    }

    private void dial(String phone) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone.trim()))));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.pt_no_dialer, Toast.LENGTH_SHORT).show();
        }
    }
}
