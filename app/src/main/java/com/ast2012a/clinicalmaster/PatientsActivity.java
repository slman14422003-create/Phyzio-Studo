package com.ast2012a.clinicalmaster;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PatientsActivity extends AppCompatActivity {

    private static final int SORT_RECENT = 0;
    private static final int SORT_NAME = 1;
    private static final int SORT_DUES = 2;
    private static final int SORT_NEXT = 3;

    private static final long SEARCH_DEBOUNCE_MS = 180;

    private LinearLayout container;
    private View contentBox;
    private View emptyBox;
    private View fab;
    private TextView noResults;
    private TextView headerSubtitle;
    private TextView statPatients;
    private TextView statSessions;
    private TextView statDues;
    private TextView sortRecent;
    private TextView sortNext;
    private TextView sortName;
    private TextView sortDues;
    private TextInputEditText searchField;
    private LayoutInflater inflater;

    private int sortMode = SORT_NEXT;
    private List<Patient> all = new ArrayList<>();
    private String currency = "";
    /** جلسات اليوم: معرّف المريض ← وقت الجلسة بالدقائق (-1 = بدون ساعة). */
    private final Map<String, Integer> todayMinutes = new HashMap<>();

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable renderListRunnable = this::renderList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patients);
        inflater = LayoutInflater.from(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_patients);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        container = findViewById(R.id.patients_container);
        contentBox = findViewById(R.id.patients_content);
        emptyBox = findViewById(R.id.patients_empty_container);
        fab = findViewById(R.id.fab_add_patient);
        noResults = findViewById(R.id.patients_no_results);
        headerSubtitle = findViewById(R.id.header_subtitle);
        statPatients = findViewById(R.id.stat_patients);
        statSessions = findViewById(R.id.stat_sessions);
        statDues = findViewById(R.id.stat_dues);
        sortRecent = findViewById(R.id.sort_recent);
        sortNext = findViewById(R.id.sort_next);
        sortName = findViewById(R.id.sort_name);
        sortDues = findViewById(R.id.sort_dues);
        searchField = findViewById(R.id.search_field);

        ((ImageView) emptyBox.findViewById(R.id.empty_icon)).setImageResource(R.drawable.ic_users);
        ((TextView) emptyBox.findViewById(R.id.empty_title)).setText(R.string.pt_empty_title);
        ((TextView) emptyBox.findViewById(R.id.empty_body)).setText(R.string.pt_empty_body);
        TextView emptyAction = emptyBox.findViewById(R.id.empty_action);
        emptyAction.setText(R.string.pt_add_patient);
        emptyAction.setOnClickListener(v -> openAddPatient());

        fab.setOnClickListener(v -> openAddPatient());
        fab.setScaleX(0f);
        fab.setScaleY(0f);
        fab.animate().scaleX(1f).scaleY(1f).setStartDelay(200).setDuration(320)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
        searchField.setHint(R.string.pt_search_hint);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchHandler.removeCallbacks(renderListRunnable);
                searchHandler.postDelayed(renderListRunnable, SEARCH_DEBOUNCE_MS);
            }
        });

        sortRecent.setOnClickListener(v -> setSort(SORT_RECENT));
        sortNext.setOnClickListener(v -> setSort(SORT_NEXT));
        sortName.setOnClickListener(v -> setSort(SORT_NAME));
        sortDues.setOnClickListener(v -> setSort(SORT_DUES));
        updateSortChips();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacks(renderListRunnable);
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_schedule) {
            openSchedule();
            return true;
        }
        if (item.getItemId() == R.id.action_currency) {
            PatientDialogs.showTextInput(this, getString(R.string.pt_currency_title),
                    getString(R.string.pt_currency_hint), getString(R.string.pt_currency_message),
                    currency, text -> {
                        PatientManager.setCurrency(this, text);
                        reload();
                    });
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void openSchedule() {
        if (Ui.isDoubleClick()) return;
        startActivity(new Intent(this, ScheduleActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void openAddPatient() {
        startActivity(new Intent(this, AddEditPatientActivity.class));
        overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void setSort(int mode) {
        sortMode = mode;
        updateSortChips();
        renderList();
    }

    private void updateSortChips() {
        styleChip(sortRecent, sortMode == SORT_RECENT);
        styleChip(sortNext, sortMode == SORT_NEXT);
        styleChip(sortName, sortMode == SORT_NAME);
        styleChip(sortDues, sortMode == SORT_DUES);
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_glass_chip_selected : R.drawable.bg_glass_chip);
        chip.setTextColor(getColor(selected ? R.color.white : R.color.text_secondary));
    }

    private void reload() {
        all = PatientManager.loadPatients(this);
        currency = PatientManager.getCurrency(this);
        todayMinutes.clear();
        for (SessionReminder.Entry e : SessionReminder.patientsToday(this, System.currentTimeMillis())) {
            if (e.id != null) todayMinutes.put(e.id, e.minutes);
        }

        boolean empty = all.isEmpty();
        contentBox.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
        fab.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (empty) {
            headerSubtitle.setVisibility(View.GONE);
            return;
        }
        headerSubtitle.setText(getString(R.string.pt_count, all.size()));
        headerSubtitle.setVisibility(View.VISIBLE);
        updateStats();
        renderList();
    }

    private void updateStats() {
        Calendar now = Calendar.getInstance();
        int month = now.get(Calendar.MONTH);
        int year = now.get(Calendar.YEAR);
        int monthSessions = 0;
        double dues = 0;
        Calendar c = Calendar.getInstance();
        for (Patient p : all) {
            for (Patient.Session s : p.sessions) {
                c.setTimeInMillis(s.date);
                if (c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year) monthSessions++;
            }
            double b = p.balance();
            if (b > 0.004) dues += b;
        }
        statPatients.setText(String.valueOf(all.size()));
        statSessions.setText(String.valueOf(monthSessions));
        statDues.setText(Fmt.money(dues, currency));
    }

    private void renderList() {
        final String q = DataManager.normalize(searchField.getText() == null ? "" : searchField.getText().toString());
        final String qDigits = digitsOnly(searchField.getText() == null ? "" : searchField.getText().toString());

        List<Patient> shown = new ArrayList<>();
        for (Patient p : all) {
            if (q.isEmpty() || matches(p, q, qDigits)) shown.add(p);
        }

        final Collator collator = Collator.getInstance(Locale.forLanguageTag("ar"));
        final long now = System.currentTimeMillis();
        Collections.sort(shown, new Comparator<Patient>() {
            @Override
            public int compare(Patient a, Patient b) {
                switch (sortMode) {
                    case SORT_NAME:
                        return collator.compare(a.displayName(), b.displayName());
                    case SORT_DUES:
                        return Double.compare(b.balance(), a.balance());
                    case SORT_NEXT: {
                        long na = a.effectiveNextAppointment(now);
                        long nb = b.effectiveNextAppointment(now);
                        if (na <= 0 && nb <= 0) return Long.compare(b.lastActivity(), a.lastActivity());
                        if (na <= 0) return 1;
                        if (nb <= 0) return -1;
                        return Long.compare(na, nb);
                    }
                    default:
                        return Long.compare(b.lastActivity(), a.lastActivity());
                }
            }
        });

        // في ترتيب "الأقرب موعدًا": جلسات اليوم أولًا (بحسب الساعة) ثم الباقي
        if (sortMode == SORT_NEXT) {
            Collections.sort(shown, new Comparator<Patient>() {
                @Override
                public int compare(Patient a, Patient b) {
                    Integer ta = todayMinutes.get(a.id);
                    Integer tb = todayMinutes.get(b.id);
                    if (ta != null && tb != null) {
                        int x = ta < 0 ? Integer.MAX_VALUE : ta;
                        int y = tb < 0 ? Integer.MAX_VALUE : tb;
                        return Integer.compare(x, y);
                    }
                    if (ta != null) return -1;
                    if (tb != null) return 1;
                    return 0;
                }
            });
        }

        container.removeAllViews();
        if (q.isEmpty()) container.addView(buildScheduleBanner());

        int lastGroup = -1;
        for (Patient p : shown) {
            if (sortMode == SORT_NEXT) {
                int g = groupOf(p, now);
                if (g != lastGroup) {
                    lastGroup = g;
                    container.addView(buildSection(g, countGroup(shown, g, now)));
                }
            }
            container.addView(buildRow(p, now));
        }
        container.setLayoutAnimation(
                android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_fall_stagger));
        container.scheduleLayoutAnimation();
        noResults.setVisibility(!all.isEmpty() && shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private int groupOf(Patient p, long now) {
        if (todayMinutes.containsKey(p.id)) return 0;
        return p.effectiveNextAppointment(now) > 0 ? 1 : 2;
    }

    private int countGroup(List<Patient> list, int g, long now) {
        int n = 0;
        for (Patient p : list) if (groupOf(p, now) == g) n++;
        return n;
    }

    private View buildSection(int group, int count) {
        View v = inflater.inflate(R.layout.item_patient_section, container, false);
        String title = group == 0 ? "اليوم" : group == 1 ? "القادمون" : "بدون موعد";
        ((TextView) v.findViewById(R.id.section_title)).setText(title);
        ((TextView) v.findViewById(R.id.section_count)).setText(String.valueOf(count));
        return v;
    }

    private View buildScheduleBanner() {
        View b = inflater.inflate(R.layout.item_schedule_banner, container, false);
        int today = todayMinutes.size();
        String sub;
        if (today == 0) sub = "عرض جلسات الأسبوع";
        else if (today == 1) sub = "جلسة واحدة اليوم · عرض الأسبوع";
        else if (today == 2) sub = "جلستان اليوم · عرض الأسبوع";
        else if (today <= 10) sub = today + " جلسات اليوم · عرض الأسبوع";
        else sub = today + " جلسة اليوم · عرض الأسبوع";
        ((TextView) b.findViewById(R.id.banner_sub)).setText(sub);
        b.setOnClickListener(v -> openSchedule());
        Ui.applyPressFeedback(b);
        return b;
    }

    private void openPatient(Patient p) {
        Intent i = new Intent(this, PatientDetailActivity.class);
        i.putExtra("patient_id", p.id);
        startActivity(i);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private boolean matches(Patient p, String q, String qDigits) {
        if (DataManager.normalize(p.name).contains(q)) return true;
        if (DataManager.normalize(p.diagnosis).contains(q)) return true;
        return !qDigits.isEmpty() && digitsOnly(p.phone).contains(qDigits);
    }

    private static String digitsOnly(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        return sb.toString();
    }

    private String nextLabel(Patient p, long now) {
        Integer tm = todayMinutes.get(p.id);
        if (tm != null) {
            return tm >= 0 ? getString(R.string.pt_today) + " · " + Fmt.minutesToTime(tm)
                    : getString(R.string.pt_today);
        }
        long t = p.effectiveNextAppointment(now);
        if (t <= 0) return "";
        int ahead = Fmt.daysBetween(now, t);
        String day;
        if (ahead == 0) day = getString(R.string.pt_today);
        else if (ahead == 1) day = getString(R.string.pt_tomorrow);
        else day = Fmt.dayDate(t);
        return p.nextAppointmentHasTime(now) ? day + " · " + Fmt.time(t) : day;
    }

    private View buildRow(final Patient p, long now) {
        View row = inflater.inflate(R.layout.item_patient_row, container, false);
        ((TextView) row.findViewById(R.id.patient_avatar)).setText(Fmt.initial(p.name));
        ((TextView) row.findViewById(R.id.patient_name)).setText(BidiText.fix(p.displayName()));

        TextView subtitle = row.findViewById(R.id.patient_subtitle);
        String sub = subtitleOf(p);
        if (sub.isEmpty()) {
            subtitle.setVisibility(View.GONE);
        } else {
            subtitle.setText(BidiText.fix(sub));
            subtitle.setVisibility(View.VISIBLE);
        }

        TextView next = row.findViewById(R.id.patient_next);
        String label = nextLabel(p, now);
        if (label.isEmpty()) {
            next.setVisibility(View.GONE);
        } else {
            next.setText(BidiText.fix(label));
            next.setVisibility(View.VISIBLE);
        }
        boolean isToday = todayMinutes.containsKey(p.id);
        next.setBackgroundResource(isToday ? R.drawable.bg_pill_primary : R.drawable.bg_capsule_soft);
        next.setTextColor(getColor(isToday ? R.color.white : R.color.primary_cyan_dark));
        TextView avatar = row.findViewById(R.id.patient_avatar);
        if (isToday) {
            avatar.setBackgroundResource(R.drawable.bg_avatar_gradient);
            avatar.setTextColor(getColor(R.color.white));
        }

        ((TextView) row.findViewById(R.id.patient_meta)).setText(
                BidiText.fix(Fmt.sessionsLabel(p.sessions.size())));

        TextView due = row.findViewById(R.id.patient_due_badge);
        double balance = p.balance();
        if (balance > 0.004) {
            due.setText(BidiText.fix(getString(R.string.pt_owes, Fmt.money(balance, currency))));
            due.setVisibility(View.VISIBLE);
        } else {
            due.setVisibility(View.GONE);
        }

        View call = row.findViewById(R.id.patient_call);
        if (p.phone.trim().isEmpty()) {
            call.setVisibility(View.GONE);
        } else {
            call.setVisibility(View.VISIBLE);
            call.setOnClickListener(v -> dial(p.phone));
            Ui.applyPressFeedback(call);
        }

        row.setOnClickListener(v -> openPatient(p));
        Ui.applyPressFeedback(row);
        return row;
    }

    private String subtitleOf(Patient p) {
        if (!p.diagnosis.trim().isEmpty()) return p.diagnosis.trim().replace('\n', ' ');
        StringBuilder sb = new StringBuilder();
        if (p.age > 0) sb.append(getString(R.string.pt_age_years, p.age));
        if (!p.gender.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p.gender);
        }
        return sb.toString();
    }

    private void dial(String phone) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone.trim()))));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.pt_no_dialer, Toast.LENGTH_SHORT).show();
        }
    }
}
