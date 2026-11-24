package com.ast2012a.clinicalmaster;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class SchedulePanel {

    private static final int[] PALETTE = {
            R.color.primary_cyan, R.color.accent_green, R.color.accent_gold,
            R.color.accent_violet, R.color.accent_red
    };

    private static final int STATUS_DONE = 0;
    private static final int STATUS_UPCOMING = 1;
    private static final int STATUS_UNLOGGED = 2;

    private static final class Item {
        final Patient patient;
        final int minutes;

        Item(Patient patient, int minutes) {
            this.patient = patient;
            this.minutes = minutes;
        }
    }

    private final Activity host;
    private final LayoutInflater inflater;
    private final LinearLayout dayStrip;
    private final LinearLayout agenda;
    private final TextView dayTitle;
    private final TextView dayCount;
    private final Runnable onDataChanged;
    private final Runnable onSelectionChanged;

    private List<Patient> patients = new ArrayList<>();
    private final long[] dayStart = new long[7];
    private final List<List<Item>> week = new ArrayList<>();
    private int todayIdx;
    private int selected;
    private boolean selectionInitialized;

    public SchedulePanel(Activity host, View root, Runnable onDataChanged, Runnable onSelectionChanged) {
        this.host = host;
        this.inflater = LayoutInflater.from(host);
        this.dayStrip = root.findViewById(R.id.day_strip);
        this.agenda = root.findViewById(R.id.agenda_container);
        this.dayTitle = root.findViewById(R.id.day_title);
        this.dayCount = root.findViewById(R.id.day_count);
        this.onDataChanged = onDataChanged;
        this.onSelectionChanged = onSelectionChanged;
    }

    public void onResume() {
        patients = PatientManager.loadPatients(host);
        buildWeek();
        if (!selectionInitialized) {
            selected = todayIdx;
            selectionInitialized = true;
        }
        renderStrip();
        renderDay();
    }

    public int getSelected() {
        return selected;
    }

    public void setSelected(int index) {
        if (index < 0 || index > 6) return;
        selected = index;
        selectionInitialized = true;
    }

    public String countLabel() {
        if (selected < 0 || selected >= week.size()) return "";
        return Fmt.sessionsLabel(week.get(selected).size());
    }

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
    }

    private void renderStrip() {
        dayStrip.removeAllViews();
        String[] shortNames = host.getResources().getStringArray(R.array.pt_sched_days_short);
        Calendar c = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            boolean sel = i == selected;
            boolean today = i == todayIdx;
            int count = week.get(i).size();

            LinearLayout cell = new LinearLayout(host);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, Ui.dp(host, 10), 0, Ui.dp(host, 8));
            cell.setBackgroundResource(sel ? R.drawable.bg_day_cell_selected
                    : today ? R.drawable.bg_day_cell_today : R.drawable.bg_day_cell);
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setOnClickListener(v -> {
                selected = idx;
                renderStrip();
                renderDay();
                if (onSelectionChanged != null) onSelectionChanged.run();
            });
            dayStrip.addView(cell, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            int main = host.getColor(sel ? R.color.white : R.color.text_primary);
            int muted = sel ? host.getColor(R.color.on_accent_muted) : host.getColor(R.color.text_tertiary);

            TextView name = new TextView(host);
            name.setText(shortNames[i]);
            name.setTextSize(11.5f);
            name.setTextColor(muted);
            name.setGravity(Gravity.CENTER);
            cell.addView(name);

            c.setTimeInMillis(dayStart[i]);
            TextView num = new TextView(host);
            num.setText(String.valueOf(c.get(Calendar.DAY_OF_MONTH)));
            num.setTextSize(18f);
            num.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD);
            num.setTextColor(today && !sel ? host.getColor(R.color.primary_cyan_dark) : main);
            num.setTextDirection(View.TEXT_DIRECTION_LTR);
            num.setGravity(Gravity.CENTER);
            num.setPadding(0, Ui.dp(host, 2), 0, Ui.dp(host, 4));
            cell.addView(num);

            TextView dot = new TextView(host);
            dot.setGravity(Gravity.CENTER);
            dot.setTextSize(10.5f);
            dot.setTypeface(null, android.graphics.Typeface.BOLD);
            dot.setTextDirection(View.TEXT_DIRECTION_LTR);
            int size = Ui.dp(host, 18);
            if (count > 0) {
                dot.setText(String.valueOf(count));
                dot.setBackgroundResource(sel ? R.drawable.bg_count_dot_light : R.drawable.bg_count_dot);
                dot.setTextColor(sel ? host.getColor(R.color.primary_cyan_dark) : host.getColor(R.color.white));
            } else {
                dot.setText("");
            }
            cell.addView(dot, new LinearLayout.LayoutParams(size, size));
        }
    }

    private void renderDay() {
        List<Item> items = week.get(selected);
        String name = Fmt.dayName(Patient.WEEK_ORDER[selected]);
        String date = Fmt.date(dayStart[selected]).substring(0, 5);
        boolean isToday = selected == todayIdx;
        dayTitle.setText(isToday ? host.getString(R.string.sched_today_title, name, date)
                : host.getString(R.string.sched_day_title, name, date));
        dayCount.setText(Fmt.sessionsLabel(items.size()));

        agenda.removeAllViews();
        if (items.isEmpty()) {
            agenda.addView(buildEmpty());
            return;
        }

        Calendar nowC = Calendar.getInstance();
        int nowMin = nowC.get(Calendar.HOUR_OF_DAY) * 60 + nowC.get(Calendar.MINUTE);
        boolean hasTimed = items.get(0).minutes >= 0;
        boolean markerAdded = !isToday || !hasTimed;
        boolean noTimeHeader = false;
        for (Item it : items) {
            if (!markerAdded && (it.minutes < 0 || it.minutes >= nowMin)) {
                agenda.addView(buildNowMarker(nowMin));
                markerAdded = true;
            }
            if (it.minutes < 0 && !noTimeHeader) {
                noTimeHeader = true;
                View h = inflater.inflate(R.layout.item_patient_section, agenda, false);
                ((TextView) h.findViewById(R.id.section_title)).setText(R.string.pt_sched_no_time);
                h.findViewById(R.id.section_count).setVisibility(View.GONE);
                agenda.addView(h);
            }
            agenda.addView(buildEntry(it, isToday && it.minutes >= 0 && it.minutes + 60 < nowMin));
        }
        if (!markerAdded) agenda.addView(buildNowMarker(nowMin));
        Motion.playOnce(agenda);
    }

    private View buildNowMarker(int nowMin) {
        View v = inflater.inflate(R.layout.item_now_marker, agenda, false);
        ((TextView) v.findViewById(R.id.now_label)).setText(
                host.getString(R.string.sched_now, Fmt.minutesToTime(nowMin)));
        return v;
    }

    private View buildEmpty() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, Ui.dp(host, 56), 0, 0);

        ImageView icon = new ImageView(host);
        icon.setImageResource(R.drawable.ic_calendar);
        icon.setColorFilter(host.getColor(R.color.text_tertiary));
        box.addView(icon, new LinearLayout.LayoutParams(Ui.dp(host, 40), Ui.dp(host, 40)));

        TextView t = new TextView(host);
        t.setText(R.string.sched_empty_day);
        t.setTextColor(host.getColor(R.color.text_secondary));
        t.setTextSize(14.5f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(host, 12), 0, 0);
        box.addView(t);
        return box;
    }

    private boolean hasSessionOnDay(Patient p, long dayStartMillis) {
        for (Patient.Session s : p.sessions) {
            if (Fmt.startOfDay(s.date) == dayStartMillis) return true;
        }
        return false;
    }

    private int statusOf(Patient p) {
        long day = Fmt.startOfDay(dayStart[selected]);
        if (hasSessionOnDay(p, day)) return STATUS_DONE;
        return selected < todayIdx ? STATUS_UNLOGGED : STATUS_UPCOMING;
    }

    private void bindStatus(TextView badge, int status) {
        if (status == STATUS_DONE) {
            badge.setText(R.string.sched_status_done);
            badge.setBackgroundResource(R.drawable.bg_capsule_soft);
            badge.setTextColor(host.getColor(R.color.accent_green));
        } else if (status == STATUS_UNLOGGED) {
            badge.setText(R.string.sched_status_unlogged);
            badge.setBackgroundResource(R.drawable.bg_badge_due);
            badge.setTextColor(host.getColor(R.color.accent_red));
        } else {
            badge.setText(R.string.sched_status_upcoming);
            badge.setBackgroundResource(R.drawable.bg_capsule_soft);
            badge.setTextColor(host.getColor(R.color.primary_cyan_dark));
        }
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
            time.setText(R.string.pt_sched_none);
            ampm.setText("");
        }

        int color = ContextCompat.getColor(host,
                PALETTE[Math.abs(p.id == null ? 0 : p.id.hashCode()) % PALETTE.length]);
        GradientDrawable bar = new GradientDrawable();
        bar.setCornerRadius(Ui.dp(host, 4));
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

        bindStatus((TextView) row.findViewById(R.id.agenda_status), statusOf(p));

        View call = row.findViewById(R.id.agenda_call);
        if (p.phone != null && !p.phone.trim().isEmpty()) {
            call.setVisibility(View.VISIBLE);
            call.setOnClickListener(v -> dial(p.phone));
        }

        View add = row.findViewById(R.id.agenda_add);
        add.setOnClickListener(v -> PatientDialogs.showSession(host, p.id, null, null,
                dayStart[selected], this::afterSessionSaved));

        View card = row.findViewById(R.id.agenda_card);
        card.setOnClickListener(v -> {
            if (Ui.isDoubleClick()) return;
            Intent i = new Intent(host, PatientDetailActivity.class);
            i.putExtra("patient_id", p.id);
            host.startActivity(i);
            Motion.forward(host);
        });
        Ui.applyPressFeedback(card);
        if (past) row.setAlpha(0.55f);
        return row;
    }

    private void afterSessionSaved() {
        onResume();
        if (onDataChanged != null) onDataChanged.run();
    }

    private void dial(String phone) {
        try {
            host.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone.trim()))));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(host, R.string.pt_no_dialer, Toast.LENGTH_SHORT).show();
        }
    }
}
