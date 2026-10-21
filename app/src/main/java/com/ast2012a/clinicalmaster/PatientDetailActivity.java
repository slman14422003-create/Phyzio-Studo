package com.ast2012a.clinicalmaster;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PatientDetailActivity extends AppCompatActivity {

    private static final int SESSIONS_PREVIEW = 5;

    private String patientId;
    private Patient patient;
    private String currency = "";
    private boolean showAllSessions = false;
    private LayoutInflater inflater;

    private LinearLayout sessionsContainer;
    private LinearLayout historyContainer;
    private LinearLayout paymentsContainer;
    private SessionCalendarView calendarView;
    private TextView monthTitle;
    private TextView calendarSummary;
    private View clearDayButton;

    private int viewYear = -1;
    private int viewMonth = -1;
    private long selectedDay = 0L;

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_detail);
        inflater = LayoutInflater.from(this);

        patientId = getIntent().getStringExtra("patient_id");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_patient_detail);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_share) {
                shareSummary();
                return true;
            }
            if (item.getItemId() == R.id.action_ask_ai) {
                askAiAboutThisPatient();
                return true;
            }
            return false;
        });

        sessionsContainer = findViewById(R.id.sessions_container);
        historyContainer = findViewById(R.id.history_container);
        paymentsContainer = findViewById(R.id.payments_container);
        calendarView = findViewById(R.id.calendar_view);
        monthTitle = findViewById(R.id.calendar_month_title);
        calendarSummary = findViewById(R.id.calendar_summary);
        clearDayButton = findViewById(R.id.btn_clear_day);

        Calendar now = Calendar.getInstance();
        viewYear = now.get(Calendar.YEAR);
        viewMonth = now.get(Calendar.MONTH);

        calendarView.setOnDayClickListener(day -> {
            selectedDay = selectedDay == day ? 0L : day;
            showAllSessions = false;
            calendarView.setSelectedDay(selectedDay);
            updateCalendarSummary();
            bindSessions();
        });
        findViewById(R.id.btn_prev_month).setOnClickListener(v -> shiftMonth(-1));
        findViewById(R.id.btn_next_month).setOnClickListener(v -> shiftMonth(1));
        clearDayButton.setOnClickListener(v -> {
            selectedDay = 0L;
            calendarView.setSelectedDay(0L);
            updateCalendarSummary();
            bindSessions();
        });

        findViewById(R.id.btn_add_session).setOnClickListener(v ->
                PatientDialogs.showSession(this, patientId, null, null, selectedDay, this::reload));
        findViewById(R.id.btn_add_payment).setOnClickListener(v ->
                PatientDialogs.showPayment(this, patientId, this::reload));
        findViewById(R.id.btn_add_assignment).setOnClickListener(v -> pickProgramToAssign());
        findViewById(R.id.btn_set_appointment).setOnClickListener(v -> pickAppointment());
        findViewById(R.id.btn_clear_appointment).setOnClickListener(v -> {
            PatientManager.setNextAppointment(this, patientId, 0);
            reload();
        });
        findViewById(R.id.btn_add_to_calendar).setOnClickListener(v -> addToCalendar());
        findViewById(R.id.btn_call).setOnClickListener(v -> openPhone("tel:"));
        findViewById(R.id.btn_sms).setOnClickListener(v -> openPhone("smsto:"));
        findViewById(R.id.btn_whatsapp).setOnClickListener(v -> openWhatsApp());
        findViewById(R.id.btn_share_summary).setOnClickListener(v -> shareSummary());
        findViewById(R.id.btn_edit_schedule).setOnClickListener(v -> openEditPatient());
        findViewById(R.id.btn_edit_patient).setOnClickListener(v -> openEditPatient());
        findViewById(R.id.btn_delete_patient).setOnClickListener(v -> confirmDeletePatient());
        findViewById(R.id.btn_more_sessions).setOnClickListener(v -> {
            showAllSessions = !showAllSessions;
            bindSessions();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void openEditPatient() {
        Intent i = new Intent(this, AddEditPatientActivity.class);
        i.putExtra("edit_patient_id", patientId);
        startActivity(i);
        overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out);
    }

    private void reload() {
        patient = PatientManager.getPatient(this, patientId);
        if (patient == null) {
            finish();
            return;
        }
        currency = PatientManager.getCurrency(this);
        bindHeader();
        bindStats();
        bindNextCard();
        bindCalendar();
        bindSessions();
        bindHistory();
        bindFinance();
        bindNotes();
    }

    private void bindHeader() {
        ((TextView) findViewById(R.id.detail_avatar)).setText(Fmt.initial(patient.name));
        ((TextView) findViewById(R.id.detail_name)).setText(BidiText.fix(patient.displayName()));

        TextView meta = findViewById(R.id.detail_meta);
        StringBuilder sb = new StringBuilder();
        if (patient.age > 0) sb.append(getString(R.string.pt_age_years, patient.age));
        if (!patient.gender.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(patient.gender);
        }
        if (sb.length() > 0) {
            meta.setText(BidiText.fix(sb.toString()));
            meta.setVisibility(View.VISIBLE);
        } else {
            meta.setVisibility(View.GONE);
        }

        boolean hasPhone = !patient.phone.trim().isEmpty();
        TextView phone = findViewById(R.id.detail_phone);
        phone.setVisibility(hasPhone ? View.VISIBLE : View.GONE);
        if (hasPhone) phone.setText(patient.phone.trim());
        findViewById(R.id.contact_row).setVisibility(hasPhone ? View.VISIBLE : View.GONE);

        boolean hasDiagnosis = !patient.diagnosis.trim().isEmpty();
        findViewById(R.id.detail_diagnosis_box).setVisibility(hasDiagnosis ? View.VISIBLE : View.GONE);
        if (hasDiagnosis) {
            ((TextView) findViewById(R.id.detail_diagnosis)).setText(BidiText.fix(patient.diagnosis.trim()));
        }
    }

    private void bindStats() {
        ((TextView) findViewById(R.id.detail_stat_sessions)).setText(String.valueOf(patient.sessions.size()));

        double drop = patient.averagePainDrop();
        ((TextView) findViewById(R.id.detail_stat_pain)).setText(
                drop <= -999 ? "—" : String.format(Locale.US, "%.1f", drop));

        Patient.Session last = patient.lastSession();
        ((TextView) findViewById(R.id.detail_stat_last)).setText(
                last == null ? "—" : BidiText.fix(Fmt.relative(last.date, System.currentTimeMillis())));
    }

    private void bindNotes() {
        boolean has = !patient.notes.trim().isEmpty();
        findViewById(R.id.notes_card).setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) ((TextView) findViewById(R.id.detail_notes)).setText(BidiText.fix(patient.notes.trim()));
    }

    private void bindNextCard() {
        TextView text = findViewById(R.id.appointment_text);
        TextView setBtn = findViewById(R.id.btn_set_appointment);
        TextView scheduleBtn = findViewById(R.id.btn_edit_schedule);
        TextView scheduleText = findViewById(R.id.schedule_text);
        View calBtn = findViewById(R.id.btn_add_to_calendar);
        View clearBtn = findViewById(R.id.btn_clear_appointment);

        String weekly = patient.hasSchedule() ? patient.scheduleLabel() : getString(R.string.pt_not_set);
        scheduleText.setText(BidiText.fix(getString(R.string.pt_weekly_label, weekly)));
        scheduleBtn.setText(patient.hasSchedule() ? R.string.pt_edit_schedule : R.string.pt_set_schedule);

        long now = System.currentTimeMillis();
        long effective = patient.effectiveNextAppointment(now);
        boolean manualFuture = patient.nextAppointment > now;

        if (effective > 0) {
            String value = patient.nextAppointmentLabel(now);
            if (!manualFuture) value += "  " + getString(R.string.pt_auto_by_schedule);
            text.setText(BidiText.fix(value));
            setBtn.setText(manualFuture ? R.string.pt_change_appointment : R.string.pt_set_another);
            calBtn.setVisibility(View.VISIBLE);
            clearBtn.setVisibility(patient.nextAppointment > 0 ? View.VISIBLE : View.GONE);
        } else if (patient.nextAppointment > 0) {
            text.setText(BidiText.fix(Fmt.dateTime(patient.nextAppointment) + "  " + getString(R.string.pt_expired)));
            setBtn.setText(R.string.pt_change_appointment);
            calBtn.setVisibility(View.VISIBLE);
            clearBtn.setVisibility(View.VISIBLE);
        } else {
            text.setText(R.string.pt_not_set);
            setBtn.setText(R.string.pt_set_appointment);
            calBtn.setVisibility(View.GONE);
            clearBtn.setVisibility(View.GONE);
        }
    }

    private void pickAppointment() {
        long now = System.currentTimeMillis();
        long auto = patient.effectiveNextAppointment(now);
        long initial = patient.nextAppointment > 0
                ? patient.nextAppointment
                : (auto > now ? auto : now + 24L * 60 * 60 * 1000);
        PatientDialogs.pickDateTime(this, initial, millis -> {
            PatientManager.setNextAppointment(this, patientId, millis);
            reload();
        });
    }

    private void addToCalendar() {
        long now = System.currentTimeMillis();
        long begin = patient.effectiveNextAppointment(now);
        boolean timed = patient.nextAppointmentHasTime(now);
        if (begin <= 0 && patient.nextAppointment > 0) {
            begin = patient.nextAppointment;
            timed = true;
        }
        if (begin <= 0) return;
        Intent i = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 45L * 60 * 1000)
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, !timed)
                .putExtra(CalendarContract.Events.TITLE,
                        getString(R.string.pt_calendar_event_title, patient.displayName()));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.pt_no_calendar_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void shiftMonth(int delta) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(viewYear, viewMonth, 1);
        c.add(Calendar.MONTH, delta);
        viewYear = c.get(Calendar.YEAR);
        viewMonth = c.get(Calendar.MONTH);
        selectedDay = 0L;
        showAllSessions = false;
        bindCalendar();
        bindSessions();
    }

    private void bindCalendar() {
        String[] months = getResources().getStringArray(R.array.cal_months);
        monthTitle.setText(getString(R.string.cal_month_title, months[viewMonth], viewYear));
        calendarView.setMonth(viewYear, viewMonth);
        calendarView.setData(patient.sessions, patient.sessionDaysMask);
        calendarView.setSelectedDay(selectedDay);
        updateCalendarSummary();
    }

    private String countAndMinutes(int count, int minutes) {
        String label = Fmt.sessionsLabel(count);
        return minutes > 0 ? label + " · " + Fmt.minutes(minutes) : label;
    }

    private void updateCalendarSummary() {
        int count = 0;
        int minutes = 0;
        Calendar c = Calendar.getInstance();
        for (Patient.Session s : patient.sessions) {
            if (selectedDay != 0L) {
                if (Fmt.startOfDay(s.date) != selectedDay) continue;
            } else {
                c.setTimeInMillis(s.date);
                if (c.get(Calendar.YEAR) != viewYear || c.get(Calendar.MONTH) != viewMonth) continue;
            }
            count++;
            minutes += Math.max(0, s.durationMin);
        }
        String text;
        if (selectedDay != 0L) {
            String day = Fmt.dayDate(selectedDay);
            text = count == 0
                    ? getString(R.string.cal_day_summary_empty, day)
                    : getString(R.string.cal_month_summary, day, countAndMinutes(count, minutes));
        } else {
            text = count == 0
                    ? getString(R.string.cal_month_summary_empty)
                    : countAndMinutes(count, minutes);
        }
        calendarSummary.setText(BidiText.fix(text));
    }

    private void bindSessions() {
        boolean filtered = selectedDay != 0L;
        List<Patient.Session> list = new ArrayList<>();
        if (filtered) {
            for (Patient.Session s : patient.sessionsOldestFirst()) {
                if (Fmt.startOfDay(s.date) == selectedDay) list.add(s);
            }
        } else {
            list = patient.sessionsNewestFirst();
        }
        sessionsContainer.removeAllViews();

        clearDayButton.setVisibility(filtered ? View.VISIBLE : View.GONE);
        TextView empty = findViewById(R.id.sessions_empty);
        empty.setText(filtered ? R.string.pt_sessions_empty_day : R.string.pt_sessions_empty);
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        int limit = filtered || showAllSessions ? list.size() : Math.min(SESSIONS_PREVIEW, list.size());
        for (int i = 0; i < limit; i++) sessionsContainer.addView(buildSessionRow(list.get(i)));

        TextView more = findViewById(R.id.btn_more_sessions);
        if (!filtered && list.size() > SESSIONS_PREVIEW) {
            more.setVisibility(View.VISIBLE);
            more.setText(showAllSessions
                    ? getString(R.string.pt_show_less)
                    : getString(R.string.pt_show_all_n, list.size()));
        } else {
            more.setVisibility(View.GONE);
        }

        List<Float> before = new ArrayList<>();
        List<Float> after = new ArrayList<>();
        for (Patient.Session s : patient.sessionsOldestFirst()) {
            if (s.hasPain()) {
                before.add((float) s.painBefore);
                after.add((float) s.painAfter);
            }
        }
        View painCard = findViewById(R.id.pain_card);
        if (before.size() >= 2) {
            float[] b = new float[before.size()];
            float[] a = new float[after.size()];
            for (int i = 0; i < b.length; i++) {
                b[i] = before.get(i);
                a[i] = after.get(i);
            }
            ((PainTrendView) findViewById(R.id.pain_trend)).setData(b, a);
            painCard.setVisibility(View.VISIBLE);
        } else {
            painCard.setVisibility(View.GONE);
        }
    }

    private View buildSessionRow(final Patient.Session s) {
        View row = inflater.inflate(R.layout.item_session_row, sessionsContainer, false);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(s.date);
        ((TextView) row.findViewById(R.id.session_day_num)).setText(
                String.format(Locale.US, "%d", c.get(Calendar.DAY_OF_MONTH)));
        ((TextView) row.findViewById(R.id.session_day_name)).setText(Fmt.dayName(c.get(Calendar.DAY_OF_WEEK)));
        ((TextView) row.findViewById(R.id.session_time)).setText(
                BidiText.fix(Fmt.date(s.date) + " · " + Fmt.time(s.date)));

        TextView duration = row.findViewById(R.id.session_duration);
        if (s.durationMin > 0) {
            duration.setText(BidiText.fix(Fmt.minutes(s.durationMin)));
            duration.setVisibility(View.VISIBLE);
        }

        TextView fee = row.findViewById(R.id.session_fee);
        if (s.fee > 0) {
            fee.setText(Fmt.money(s.fee, currency));
            fee.setVisibility(View.VISIBLE);
        }

        TextView protocol = row.findViewById(R.id.session_protocol);
        if (s.protocol.trim().isEmpty()) {
            protocol.setVisibility(View.GONE);
        } else {
            protocol.setText(BidiText.fix(s.protocol.trim()));
            final CaseItem quick = findCaseByTitle(s.protocol);
            if (quick != null) {
                protocol.setTextColor(getColor(R.color.primary_cyan_dark));
                protocol.setOnClickListener(v -> openCase(quick));
            }
        }

        LinearLayout chips = row.findViewById(R.id.session_chips);
        if (s.hasPain()) {
            addSessionChip(chips, getString(R.string.pt_pain_chip, s.painBefore, s.painAfter));
        }
        if (chips.getChildCount() == 0) chips.setVisibility(View.GONE);

        TextView notes = row.findViewById(R.id.session_notes);
        if (!s.notes.trim().isEmpty()) {
            notes.setText(BidiText.fix(s.notes.trim()));
            notes.setVisibility(View.VISIBLE);
        }

        row.setOnClickListener(v -> PatientDialogs.showSession(this, patientId, s, null, this::reload));
        row.findViewById(R.id.session_delete).setOnClickListener(v -> confirmDeleteSession(s));
        return row;
    }

    private void addSessionChip(LinearLayout parent, String text) {
        TextView chip = (TextView) inflater.inflate(R.layout.item_badge, parent, false);
        chip.setText(BidiText.fix(text));
        chip.setTextDirection(View.TEXT_DIRECTION_RTL);
        chip.setBackgroundResource(R.drawable.bg_badge_ems);
        chip.setTextColor(getColor(R.color.m3_on_primary_container));
        parent.addView(chip);
    }

    private void confirmDeleteSession(final Patient.Session s) {
        new ClaudeDialog(this)
                .setTitle(getString(R.string.pt_delete_session_title))
                .setMessage(getString(R.string.pt_delete_session_message))
                .setPositiveButton(getString(R.string.pt_delete), (dialog, which) -> {
                    PatientManager.deleteSession(this, patientId, s.id);
                    reload();
                })
                .setNegativeButton(getString(R.string.pt_cancel), null)
                .show();
    }

    private static class ProtocolUse {
        int count;
        long lastDate;
    }

    private void bindHistory() {
        historyContainer.removeAllViews();
        long now = System.currentTimeMillis();
        int rows = 0;

        for (final Patient.Assignment a : patient.assignments) {
            final TreatmentProgram program = findProgram(a.refId);
            View row = inflater.inflate(R.layout.item_history_row, historyContainer, false);
            ((ImageView) row.findViewById(R.id.history_icon)).setImageResource(R.drawable.ic_clipboard);
            String title = program != null ? program.title : a.title;
            if (title.trim().isEmpty()) title = getString(R.string.pt_untitled_program);
            ((TextView) row.findViewById(R.id.history_title)).setText(BidiText.fix(title));
            String sub = getString(program != null ? R.string.pt_program_sub : R.string.pt_program_deleted_sub,
                    Fmt.relative(a.date, now));
            ((TextView) row.findViewById(R.id.history_sub)).setText(BidiText.fix(sub));
            row.setOnClickListener(v -> {
                if (program == null) {
                    Toast.makeText(this, R.string.pt_program_missing, Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent i = new Intent(this, TreatmentProgramDetailActivity.class);
                i.putExtra("program_id", program.id);
                startActivity(i);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
            row.setOnLongClickListener(v -> {
                confirmRemoveAssignment(a);
                return true;
            });
            historyContainer.addView(row);
            rows++;
        }

        Map<String, ProtocolUse> uses = new LinkedHashMap<>();
        for (Patient.Session s : patient.sessionsNewestFirst()) {
            String key = s.protocol.trim();
            if (key.isEmpty()) continue;
            ProtocolUse u = uses.get(key);
            if (u == null) {
                u = new ProtocolUse();
                u.lastDate = s.date;
                uses.put(key, u);
            }
            u.count++;
        }
        for (Map.Entry<String, ProtocolUse> e : uses.entrySet()) {
            final String protocolTitle = e.getKey();
            ProtocolUse u = e.getValue();
            View row = inflater.inflate(R.layout.item_history_row, historyContainer, false);
            ((ImageView) row.findViewById(R.id.history_icon)).setImageResource(R.drawable.ic_zap);
            ((TextView) row.findViewById(R.id.history_title)).setText(BidiText.fix(protocolTitle));
            ((TextView) row.findViewById(R.id.history_sub)).setText(BidiText.fix(
                    getString(R.string.pt_protocol_use_sub, Fmt.sessionsLabel(u.count),
                            Fmt.relative(u.lastDate, now))));
            row.setOnClickListener(v -> {
                CaseItem c = findCaseByTitle(protocolTitle);
                if (c == null) {
                    Toast.makeText(this, R.string.pt_protocol_missing, Toast.LENGTH_SHORT).show();
                } else {
                    openCase(c);
                }
            });
            historyContainer.addView(row);
            rows++;
        }

        findViewById(R.id.history_empty).setVisibility(rows == 0 ? View.VISIBLE : View.GONE);
    }

    private void pickProgramToAssign() {
        final List<TreatmentProgram> programs = TreatmentProgramManager.loadPrograms(this);
        if (programs.isEmpty()) {
            Toast.makeText(this, R.string.pt_no_programs, Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[programs.size()];
        for (int i = 0; i < names.length; i++) {
            String t = programs.get(i).title;
            names[i] = t.trim().isEmpty() ? getString(R.string.pt_untitled_program) : t;
        }
        new ClaudeDialog(this)
                .setTitle(getString(R.string.pt_link_program_title))
                .setItems(names, (dialog, which) -> {
                    TreatmentProgram p = programs.get(which);
                    boolean added = PatientManager.addAssignment(this, patientId,
                            Patient.Assignment.create(Patient.Assignment.TYPE_PROGRAM, p.id, p.title));
                    Toast.makeText(this,
                            added ? R.string.pt_program_linked : R.string.pt_program_already_linked,
                            Toast.LENGTH_SHORT).show();
                    reload();
                })
                .show();
    }

    private void confirmRemoveAssignment(final Patient.Assignment a) {
        new ClaudeDialog(this)
                .setTitle(getString(R.string.pt_unlink_title))
                .setMessage(getString(R.string.pt_unlink_message))
                .setPositiveButton(getString(R.string.pt_unlink), (dialog, which) -> {
                    PatientManager.removeAssignment(this, patientId, a.id);
                    reload();
                })
                .setNegativeButton(getString(R.string.pt_cancel), null)
                .show();
    }

    private TreatmentProgram findProgram(String id) {
        if (id == null || id.isEmpty()) return null;
        for (TreatmentProgram t : TreatmentProgramManager.loadPrograms(this)) {
            if (id.equals(t.id)) return t;
        }
        return null;
    }

    private CaseItem findCaseByTitle(String title) {
        if (title == null || title.trim().isEmpty()) return null;
        String wanted = title.trim();
        List<CaseItem> all = DataManager.allCases(this);
        for (CaseItem c : all) {
            if (c.title != null && wanted.equals(c.title.trim())) return c;
        }
        String w = squash(wanted);
        if (w.isEmpty()) return null;
        for (CaseItem c : all) {
            if (c.title != null && w.equals(squash(c.title))) return c;
        }
        CaseItem best = null;
        int bestLen = Integer.MAX_VALUE;
        for (CaseItem c : all) {
            if (c.title == null) continue;
            String t = squash(c.title);
            if (t.isEmpty()) continue;
            if ((t.contains(w) || w.contains(t)) && t.length() < bestLen) {
                best = c;
                bestLen = t.length();
            }
        }
        return best;
    }

    private static String squash(String s) {
        return DataManager.normalize(s).replaceAll("[\\s\\p{Punct}\u060C\u061B\u061F\u0640\u064B-\u065F]+", "");
    }

    private void openCase(CaseItem c) {
        Intent i = new Intent(this, CaseDetailActivity.class);
        i.putExtra("case_id", c.id);
        i.putExtra("case_title", c.title);
        i.putExtra("is_custom", c.custom);
        startActivity(i);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void bindFinance() {
        double charged = patient.totalCharged();
        double paid = patient.totalPaid();
        double balance = patient.balance();

        ((TextView) findViewById(R.id.finance_charged)).setText(Fmt.money(charged, currency));
        ((TextView) findViewById(R.id.finance_paid)).setText(Fmt.money(paid, currency));

        TextView balanceView = findViewById(R.id.finance_balance);
        TextView balanceLabel = findViewById(R.id.finance_balance_label);
        if (balance < -0.004) {
            balanceLabel.setText(R.string.pt_finance_credit);
            balanceView.setText(Fmt.money(-balance, currency));
            balanceView.setTextColor(getColor(R.color.accent_green));
        } else if (balance > 0.004) {
            balanceLabel.setText(R.string.pt_finance_remaining);
            balanceView.setText(Fmt.money(balance, currency));
            balanceView.setTextColor(getColor(R.color.accent_red));
        } else {
            balanceLabel.setText(R.string.pt_finance_remaining);
            balanceView.setText(Fmt.money(0, currency));
            balanceView.setTextColor(getColor(R.color.accent_green));
        }

        paymentsContainer.removeAllViews();
        List<Patient.Payment> sorted = new ArrayList<>(patient.payments);
        Collections.sort(sorted, new Comparator<Patient.Payment>() {
            @Override
            public int compare(Patient.Payment a, Patient.Payment b) {
                return Long.compare(b.date, a.date);
            }
        });
        for (final Patient.Payment p : sorted) {
            View row = inflater.inflate(R.layout.item_payment_row, paymentsContainer, false);
            ((TextView) row.findViewById(R.id.payment_date)).setText(BidiText.fix(Fmt.date(p.date)));
            ((TextView) row.findViewById(R.id.payment_amount)).setText(Fmt.money(p.amount, currency));
            TextView note = row.findViewById(R.id.payment_note);
            if (!p.note.trim().isEmpty()) {
                note.setText(BidiText.fix(p.note.trim()));
                note.setVisibility(View.VISIBLE);
            }
            row.findViewById(R.id.payment_delete).setOnClickListener(v -> confirmDeletePayment(p));
            paymentsContainer.addView(row);
        }
        findViewById(R.id.payments_empty).setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmDeletePayment(final Patient.Payment p) {
        new ClaudeDialog(this)
                .setTitle(getString(R.string.pt_delete_payment_title))
                .setMessage(getString(R.string.pt_delete_payment_message, Fmt.money(p.amount, currency)))
                .setPositiveButton(getString(R.string.pt_delete), (dialog, which) -> {
                    PatientManager.deletePayment(this, patientId, p.id);
                    reload();
                })
                .setNegativeButton(getString(R.string.pt_cancel), null)
                .show();
    }

    private void askAiAboutThisPatient() {
        if (patient == null) return;
        Intent i = new Intent(this, AiAssistantActivity.class);
        i.putExtra("patient_id", patientId);
        i.putExtra("prefill_query", getString(R.string.pt_ai_prefill));
        startActivity(i);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void openPhone(String scheme) {
        try {
            startActivity(new Intent(scheme.startsWith("tel") ? Intent.ACTION_DIAL : Intent.ACTION_SENDTO,
                    Uri.parse(scheme + Uri.encode(patient.phone.trim()))));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.pt_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void openWhatsApp() {
        String raw = patient.phone.trim().replace(" ", "").replace("-", "");
        String digits;
        if (raw.startsWith("+")) {
            digits = raw.substring(1);
        } else if (raw.startsWith("00")) {
            digits = raw.substring(2);
        } else {
            Toast.makeText(this, R.string.pt_whatsapp_intl, Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            char ch = digits.charAt(i);
            if (ch >= '0' && ch <= '9') clean.append(ch);
        }
        if (clean.length() < 7) {
            Toast.makeText(this, R.string.pt_whatsapp_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + clean)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.pt_whatsapp_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSummary() {
        if (patient == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.pt_share_header, patient.displayName())).append('\n');
        if (patient.age > 0) sb.append(getString(R.string.pt_share_age, patient.age)).append('\n');
        if (!patient.gender.isEmpty()) sb.append(getString(R.string.pt_share_gender, patient.gender)).append('\n');
        if (!patient.diagnosis.trim().isEmpty()) {
            sb.append(getString(R.string.pt_share_diagnosis, patient.diagnosis.trim())).append('\n');
        }
        sb.append(getString(R.string.pt_share_sessions_count, patient.sessions.size())).append('\n');
        double drop = patient.averagePainDrop();
        if (drop > -999) {
            sb.append(getString(R.string.pt_share_pain_avg, String.format(Locale.US, "%.1f", drop))).append('\n');
        }

        List<Patient.Session> list = patient.sessionsNewestFirst();
        if (!list.isEmpty()) {
            sb.append('\n').append(getString(R.string.pt_share_last_sessions)).append('\n');
            int n = Math.min(10, list.size());
            for (int i = 0; i < n; i++) {
                Patient.Session s = list.get(i);
                sb.append("• ").append(Fmt.date(s.date));
                if (!s.protocol.trim().isEmpty()) sb.append(" — ").append(s.protocol.trim());
                if (s.hasPain()) sb.append(getString(R.string.pt_share_pain_item, s.painBefore, s.painAfter));
                if (s.durationMin > 0) sb.append(" — ").append(Fmt.minutes(s.durationMin));
                sb.append('\n');
            }
        }

        if (patient.totalCharged() > 0 || patient.totalPaid() > 0) {
            sb.append('\n').append(getString(R.string.pt_share_account)).append('\n');
            sb.append(getString(R.string.pt_share_total, Fmt.money(patient.totalCharged(), currency))).append('\n');
            sb.append(getString(R.string.pt_share_paid, Fmt.money(patient.totalPaid(), currency))).append('\n');
            double balance = patient.balance();
            if (balance > 0.004) {
                sb.append(getString(R.string.pt_share_remaining, Fmt.money(balance, currency))).append('\n');
            } else if (balance < -0.004) {
                sb.append(getString(R.string.pt_share_credit, Fmt.money(-balance, currency))).append('\n');
            }
        }
        if (patient.hasSchedule()) {
            sb.append('\n').append(getString(R.string.pt_share_schedule, patient.scheduleLabel())).append('\n');
        }
        String nextLabel = patient.nextAppointmentLabel(System.currentTimeMillis());
        if (!nextLabel.isEmpty()) {
            sb.append('\n').append(getString(R.string.pt_share_next, nextLabel)).append('\n');
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.pt_share_header, patient.displayName()));
        send.putExtra(Intent.EXTRA_TEXT, sb.toString().trim());
        startActivity(Intent.createChooser(send, getString(R.string.pt_share_chooser)));
    }

    private void confirmDeletePatient() {
        new ClaudeDialog(this)
                .setTitle(getString(R.string.pt_delete_patient_title))
                .setMessage(getString(R.string.pt_delete_patient_message, patient.displayName()))
                .setPositiveButton(getString(R.string.pt_delete), (dialog, which) -> {
                    PatientManager.deletePatient(this, patientId);
                    Toast.makeText(this, R.string.pt_patient_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(getString(R.string.pt_cancel), null)
                .show();
    }
}
