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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
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

    public static final String EXTRA_TAB = "tab";
    private static final String STATE_TAB = "hub_tab";
    private static final int TAB_PATIENTS = 0;
    private static final int TAB_SCHEDULE = 1;
    private static final long TAB_FADE_MS = 150;
    private static final long SEARCH_DEBOUNCE_MS = 180;

    private LinearLayout container;
    private View contentBox;
    private View emptyBox;
    private View fab;
    private TextView noResults;
    private TextView headerSubtitle;
    private TextView statsLine;
    private TextView sortButton;
    private View panelPatients;
    private View panelSchedule;
    private TabLayout tabs;
    private SchedulePanel schedulePanel;
    private int currentTab = TAB_PATIENTS;
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
        contentBox = findViewById(R.id.panel_patients);
        panelPatients = contentBox;
        panelSchedule = findViewById(R.id.panel_schedule);
        emptyBox = findViewById(R.id.patients_empty_container);
        fab = findViewById(R.id.fab_add_patient);
        noResults = findViewById(R.id.patients_no_results);
        headerSubtitle = findViewById(R.id.header_subtitle);
        statsLine = findViewById(R.id.stats_line);
        sortButton = findViewById(R.id.sort_button);
        searchField = findViewById(R.id.search_field);

        ((ImageView) emptyBox.findViewById(R.id.empty_icon)).setImageResource(R.drawable.ic_users);
        ((TextView) emptyBox.findViewById(R.id.empty_title)).setText(R.string.pt_empty_title);
        ((TextView) emptyBox.findViewById(R.id.empty_body)).setText(R.string.pt_empty_body);
        TextView emptyAction = emptyBox.findViewById(R.id.empty_action);
        emptyAction.setText(R.string.pt_add_patient);
        emptyAction.setOnClickListener(v -> openAddPatient());

        fab.setOnClickListener(v -> openAddPatient());
        Motion.fabIn(fab);
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

        sortButton.setOnClickListener(this::showSortMenu);
        updateSortLabel();

        schedulePanel = new SchedulePanel(this, panelSchedule, this::reload, this::updateSubtitle);

        int startTab = getIntent().getIntExtra(EXTRA_TAB, TAB_PATIENTS);
        if (savedInstanceState != null) startTab = savedInstanceState.getInt(STATE_TAB, startTab);
        currentTab = startTab == TAB_SCHEDULE ? TAB_SCHEDULE : TAB_PATIENTS;
        setupTabs();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    private void setupTabs() {
        tabs = findViewById(R.id.tab_hub);
        tabs.addTab(tabs.newTab().setText(R.string.pt_tab_patients));
        tabs.addTab(tabs.newTab().setText(R.string.pt_tab_schedule));
        TabLayout.Tab sel = tabs.getTabAt(currentTab);
        if (sel != null) sel.select();
        applyTabVisibility(false);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void switchTab(int index) {
        if (index == currentTab) return;
        currentTab = index;
        applyTabVisibility(true);
        updateEmptyAndFab();
        updateSubtitle();
    }

    private void applyTabVisibility(boolean animate) {
        final View show = currentTab == TAB_SCHEDULE ? panelSchedule : panelPatients;
        final View hide = currentTab == TAB_SCHEDULE ? panelPatients : panelSchedule;
        hide.animate().cancel();
        show.animate().cancel();
        if (!animate) {
            show.setAlpha(1f);
            show.setVisibility(View.VISIBLE);
            hide.setAlpha(1f);
            hide.setVisibility(View.GONE);
            return;
        }
        show.setAlpha(0f);
        show.setVisibility(View.VISIBLE);
        show.animate().alpha(1f).setDuration(TAB_FADE_MS).start();
        hide.animate().alpha(0f).setDuration(TAB_FADE_MS).withEndAction(() -> {
            hide.setVisibility(View.GONE);
            hide.setAlpha(1f);
        }).start();
    }

    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu m = popup.getMenu();
        m.add(0, SORT_NEXT, 0, R.string.pt_sort_next);
        m.add(0, SORT_RECENT, 1, R.string.pt_sort_recent);
        m.add(0, SORT_NAME, 2, R.string.pt_sort_name);
        m.add(0, SORT_DUES, 3, R.string.pt_sort_dues);
        m.setGroupCheckable(0, true, true);
        MenuItem current = m.findItem(sortMode);
        if (current != null) current.setChecked(true);
        popup.setOnMenuItemClickListener(item -> {
            setSort(item.getItemId());
            return true;
        });
        popup.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacks(renderListRunnable);
    }

    private boolean onMenuItemClick(MenuItem item) {
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
        schedulePanel.onResume();
        updateSubtitle();
    }

    private void openAddPatient() {
        startActivity(new Intent(this, AddEditPatientActivity.class));
        Motion.forward(this);
    }

    @Override
    public void finish() {
        super.finish();
        Motion.back(this);
    }

    private void setSort(int mode) {
        sortMode = mode;
        updateSortLabel();
        renderList();
    }

    private void updateSortLabel() {
        int label;
        switch (sortMode) {
            case SORT_RECENT:
                label = R.string.pt_sort_recent;
                break;
            case SORT_NAME:
                label = R.string.pt_sort_name;
                break;
            case SORT_DUES:
                label = R.string.pt_sort_dues;
                break;
            default:
                label = R.string.pt_sort_next;
                break;
        }
        sortButton.setText(label);
    }

    private void updateEmptyAndFab() {
        boolean empty = all.isEmpty();
        boolean onPatients = currentTab == TAB_PATIENTS;
        contentBox.setVisibility(onPatients ? (empty ? View.GONE : View.VISIBLE) : View.GONE);
        emptyBox.setVisibility(onPatients && empty ? View.VISIBLE : View.GONE);
        fab.setVisibility(onPatients && empty ? View.GONE : View.VISIBLE);
    }

    private void updateSubtitle() {
        String text = "";
        if (currentTab == TAB_SCHEDULE) {
            text = schedulePanel.countLabel();
        } else if (!all.isEmpty()) {
            text = getString(R.string.pt_count, all.size());
        }
        if (text.isEmpty()) {
            headerSubtitle.setVisibility(View.GONE);
        } else {
            headerSubtitle.setText(text);
            headerSubtitle.setVisibility(View.VISIBLE);
        }
    }

    private void reload() {
        all = PatientManager.loadPatients(this);
        currency = PatientManager.getCurrency(this);
        todayMinutes.clear();
        for (SessionReminder.Entry e : SessionReminder.patientsToday(this, System.currentTimeMillis())) {
            if (e.id != null) todayMinutes.put(e.id, e.minutes);
        }

        updateEmptyAndFab();
        updateSubtitle();
        if (all.isEmpty()) return;
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
        statsLine.setText(BidiText.fix(getString(R.string.pt_summary_line,
                all.size(), monthSessions, Fmt.money(dues, currency))));
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
        Motion.playOnce(container);
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
        int title = group == 0 ? R.string.pt_section_today
                : group == 1 ? R.string.pt_section_upcoming : R.string.pt_section_no_appt;
        ((TextView) v.findViewById(R.id.section_title)).setText(title);
        ((TextView) v.findViewById(R.id.section_count)).setText(String.valueOf(count));
        return v;
    }

    private void openPatient(Patient p) {
        Intent i = new Intent(this, PatientDetailActivity.class);
        i.putExtra("patient_id", p.id);
        startActivity(i);
        Motion.forward(this);
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
