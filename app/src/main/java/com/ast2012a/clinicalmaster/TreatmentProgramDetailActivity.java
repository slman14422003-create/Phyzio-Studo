package com.ast2012a.clinicalmaster;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * تفاصيل برنامج علاج فيزيائي: العنوان والتشخيص ثم الأهداف والمراحل والتمارين
 * والاحتياطات وخطة الجلسات والملاحظات، مع مشاركة نصية وإسناد لمريض.
 */
public class TreatmentProgramDetailActivity extends AppCompatActivity {

    private String programId;
    private TreatmentProgram program;
    private LinearLayout container;
    private LayoutInflater inflater;

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_treatment_program_detail);
        inflater = LayoutInflater.from(this);

        programId = getIntent().getStringExtra("program_id");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_share);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_share) {
                shareProgram();
                return true;
            }
            return false;
        });

        container = findViewById(R.id.program_detail_container);
    }

    @Override
    protected void onResume() {
        super.onResume();
        program = null;
        for (TreatmentProgram t : TreatmentProgramManager.loadPrograms(this)) {
            if (t.id != null && t.id.equals(programId)) {
                program = t;
                break;
            }
        }
        if (program == null) {
            finish();
            return;
        }
        render();
    }

    private boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private void render() {
        container.removeAllViews();

        View hero = inflater.inflate(R.layout.item_detail_hero, container, false);
        LinearLayout badges = hero.findViewById(R.id.hero_badges);
        if (hero instanceof LinearLayout) {
            IllustrationView ill = new IllustrationView(this);
            ill.setScene(IllustrationView.EXERCISE);
            ((LinearLayout) hero).addView(ill, 0, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 124)));
        }
        ((TextView) hero.findViewById(R.id.hero_title)).setText(
                BidiText.fix(notEmpty(program.title) ? program.title.trim() : getString(R.string.untitled_program)));

        addChip(badges, program.diagnosis, true);
        addChip(badges, program.audience, false);
        addChip(badges, program.stage, false);
        if (badges.getChildCount() == 0) badges.setVisibility(View.GONE);

        TextView date = hero.findViewById(R.id.hero_english);
        date.setTextDirection(View.TEXT_DIRECTION_RTL);
        date.setText(BidiText.fix(getString(R.string.lbl_created) + " " + Fmt.date(program.createdAt)));
        date.setVisibility(View.VISIBLE);
        container.addView(hero);

        addOverview();

        addSection(R.string.lbl_goals, program.goals, false);
        addSection(R.string.lbl_phases, program.phases, false);
        addSection(R.string.lbl_exercises, program.exercises, false);
        addSection(R.string.lbl_equipment, program.equipment, false);
        addSection(R.string.lbl_sessions_plan, program.sessionsPlan, false);
        addSection(R.string.lbl_progression, program.progression, false);
        addSection(R.string.lbl_followup, program.followUp, false);
        addSection(R.string.lbl_home, program.homeProgram, false);
        addSection(R.string.lbl_precautions, program.precautions, true);
        addSection(R.string.lbl_notes, program.notes, false);

        View actions = inflater.inflate(R.layout.item_program_actions, container, false);
        actions.findViewById(R.id.btn_assign_patient).setOnClickListener(v -> assignToPatient());
        actions.findViewById(R.id.btn_program_edit).setOnClickListener(v -> {
            Intent i = new Intent(this, AddEditTreatmentProgramActivity.class);
            i.putExtra("edit_program_id", program.id);
            startActivity(i);
            overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out);
        });
        actions.findViewById(R.id.btn_program_delete).setOnClickListener(v -> confirmDelete());
        container.addView(actions);
    }

    private void addChip(LinearLayout badges, String text, boolean primary) {
        if (!notEmpty(text)) return;
        TextView chip = (TextView) inflater.inflate(R.layout.item_badge, badges, false);
        chip.setText(BidiText.fix(text.trim().replace('\n', ' ')));
        chip.setTextDirection(View.TEXT_DIRECTION_RTL);
        chip.setTextSize(12.5f);
        chip.setBackgroundResource(primary ? R.drawable.bg_source_chip : R.drawable.bg_badge_tens);
        chip.setTextColor(getColor(primary ? R.color.m3_on_primary_container : R.color.m3_on_secondary_container));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) chip.getLayoutParams();
        lp.setMarginEnd(Ui.dp(this, 6));
        badges.addView(chip);
    }

    private void addOverview() {
        View card = inflater.inflate(R.layout.item_spec_card, container, false);
        LinearLayout rows = card.findViewById(R.id.spec_rows);
        boolean first = true;

        String regions = CaseItem.joinList(program.regions);
        if (notEmpty(regions)) {
            addSpec(rows, R.string.lbl_region, regions, first);
            first = false;
        }
        String modalities = CaseItem.joinList(program.modalities);
        if (notEmpty(modalities)) {
            addSpec(rows, R.string.lbl_modalities, modalities, first);
            first = false;
        }
        if (program.durationWeeks > 0) {
            addSpec(rows, R.string.lbl_weeks, getString(R.string.fmt_weeks, program.durationWeeks), first);
            first = false;
        }
        if (program.sessionsPerWeek > 0) {
            addSpec(rows, R.string.lbl_sessions_week, getString(R.string.fmt_per_week, program.sessionsPerWeek), first);
            first = false;
        }
        if (notEmpty(program.sessionDuration)) {
            addSpec(rows, R.string.lbl_session_duration, program.sessionDuration, first);
            first = false;
        }
        String outcomes = CaseItem.joinList(program.outcomes);
        if (notEmpty(outcomes)) {
            addSpec(rows, R.string.lbl_outcomes, outcomes, first);
            first = false;
        }
        if (rows.getChildCount() > 0) container.addView(card);
    }

    private void addSpec(LinearLayout rows, int labelRes, String value, boolean first) {
        View row = inflater.inflate(R.layout.item_spec_row, rows, false);
        ((TextView) row.findViewById(R.id.spec_label)).setText(labelRes);
        if (first) row.findViewById(R.id.spec_divider).setVisibility(View.GONE);
        rows.addView(row);
        LinearLayout values = row.findViewById(R.id.spec_values);
        TextView v = (TextView) inflater.inflate(R.layout.item_plain_value, values, false);
        v.setText(BidiText.fix(value));
        v.setTextSize(15f);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        values.addView(v);
    }

    /** نص من سطر واحد = فقرة؛ عدة أسطر = قائمة نقطية. */
    private void addSection(int titleRes, String text, boolean warning) {
        if (!notEmpty(text)) return;
        String title = getString(titleRes);
        String[] lines = text.trim().split("\\r?\\n");
        int count = 0;
        for (String l : lines) if (!l.trim().isEmpty()) count++;

        if (count <= 1) {
            View card = inflater.inflate(R.layout.item_text_card, container, false);
            TextView label = card.findViewById(R.id.text_card_label);
            TextView body = card.findViewById(R.id.text_card_body);
            label.setText(title);
            body.setText(BidiText.fix(text.trim()));
            if (warning) {
                card.setBackgroundResource(R.drawable.bg_glass_card_warning);
                label.setTextColor(getColor(R.color.accent_red));
            }
            container.addView(card);
            return;
        }

        View card = inflater.inflate(R.layout.item_list_card, container, false);
        if (warning) card.setBackgroundResource(R.drawable.bg_glass_card_warning);
        TextView titleView = card.findViewById(R.id.list_title);
        titleView.setText(title);
        if (warning) titleView.setTextColor(getColor(R.color.accent_red));
        LinearLayout list = card.findViewById(R.id.list_container);
        for (String l : lines) {
            String clean = l.trim().replaceFirst("^[\\-\\u2022*]\\s+", "");
            if (clean.isEmpty()) continue;
            View row = inflater.inflate(R.layout.item_bullet_row, list, false);
            ((TextView) row.findViewById(R.id.bullet_text)).setText(BidiText.fix(clean));
            list.addView(row);
        }
        container.addView(card);
    }

    private void assignToPatient() {
        PatientDialogs.pickPatient(this, "إسناد البرنامج لأي مريض؟", patient -> {
            boolean added = PatientManager.addAssignment(this, patient.id,
                    Patient.Assignment.create(Patient.Assignment.TYPE_PROGRAM, program.id, program.title));
            Toast.makeText(this,
                    added ? "تم إسناد البرنامج إلى " + patient.displayName() + "."
                            : "هذا البرنامج مسند لهذا المريض بالفعل.",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void shareProgram() {
        if (program == null) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, program.title);
        send.putExtra(Intent.EXTRA_TEXT, program.toShareText(this));
        startActivity(Intent.createChooser(send, "\u0645\u0634\u0627\u0631\u0643\u0629 \u0627\u0644\u0628\u0631\u0646\u0627\u0645\u062c"));
    }



    private void confirmDelete() {
        new ClaudeDialog(this)
                .setTitle("حذف برنامج العلاج")
                .setMessage("هل تريد حذف \"" + program.title + "\" نهائيًا؟")
                .setPositiveButton("حذف", (dialog, which) -> {
                    TreatmentProgramManager.deleteProgram(this, program.id);
                    Toast.makeText(this, "تم الحذف.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }
}
