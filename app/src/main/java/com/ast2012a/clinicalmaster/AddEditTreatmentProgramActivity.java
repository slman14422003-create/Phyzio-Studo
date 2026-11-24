package com.ast2012a.clinicalmaster;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

public class AddEditTreatmentProgramActivity extends AppCompatActivity {

    private EditText fTitle, fDiagnosis, fGoals, fPhases, fExercises, fEquipment, fSessionsPlan,
            fProgression, fFollowUp, fHome, fPrecautions, fNotes;
    private FormKit.Choice cAudience, cRegion, cStage, cModalities, cDuration, cOutcomes;
    private FormKit.Stepper sWeeks, sPerWeek;
    private String editingId = null;
    private MaterialToolbar toolbar;
    private TextView submitBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_treatment_program);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        buildForm();

        submitBtn = findViewById(R.id.btn_submit_program);
        TextView cancelBtn = findViewById(R.id.btn_cancel_program);
        cancelBtn.setOnClickListener(v -> finish());

        editingId = getIntent().getStringExtra("edit_program_id");
        if (editingId != null) {
            toolbar.setTitle(R.string.program_form_edit);
            submitBtn.setText(R.string.form_save_changes);
            loadForEdit(editingId);
        }

        submitBtn.setOnClickListener(v -> submit());
    }

    private void buildForm() {
        LinearLayout root = findViewById(R.id.form_root);
        FormKit k = new FormKit(this, root);
        k.banner(IllustrationView.EXERCISE, 124);
        k.intro(R.string.program_form_intro);

        k.section(R.string.sec_program_about);
        fTitle = k.text(R.string.lbl_program_title, 0, true, 1);
        fDiagnosis = k.text(R.string.lbl_diagnosis, 0, false, 1);
        fGoals = k.textWithQuick(R.string.lbl_goals, R.string.hint_free, false, 3, R.array.opt_goals_quick);

        k.section(R.string.sec_program_target);
        cAudience = k.single(R.string.lbl_audience, R.array.opt_audience);
        cRegion = k.multi(R.string.lbl_region, R.array.opt_region);
        cStage = k.single(R.string.lbl_stage, R.array.opt_stage);

        k.section(R.string.sec_program_schedule);
        sWeeks = k.stepper(R.string.lbl_weeks, R.string.unit_weeks, 52);
        sPerWeek = k.stepper(R.string.lbl_sessions_week, R.string.unit_per_week, 7);
        cDuration = k.single(R.string.lbl_session_duration, R.array.opt_duration);
        cModalities = k.multi(R.string.lbl_modalities, R.array.opt_modalities);

        k.section(R.string.sec_program_plan);
        fPhases = k.textWithQuick(R.string.lbl_phases, R.string.hint_phases, false, 4, R.array.opt_phase_templates);
        fExercises = k.textWithQuick(R.string.lbl_exercises, R.string.hint_exercises, false, 4, R.array.opt_exercise_templates);
        fEquipment = k.textWithQuick(R.string.lbl_equipment, R.string.hint_free, false, 2, R.array.opt_equipment_quick);
        fSessionsPlan = k.text(R.string.lbl_sessions_plan, R.string.hint_free, false, 3);

        k.section(R.string.sec_program_measure);
        cOutcomes = k.multi(R.string.lbl_outcomes, R.array.opt_outcomes);
        fProgression = k.textWithQuick(R.string.lbl_progression, R.string.hint_free, false, 3, R.array.opt_progression_quick);
        fFollowUp = k.text(R.string.lbl_followup, R.string.hint_free, false, 2);

        k.section(R.string.sec_program_safety);
        fPrecautions = k.textWithQuick(R.string.lbl_precautions, R.string.hint_free, false, 3, R.array.opt_precautions_quick);
        fHome = k.textWithQuick(R.string.lbl_home, R.string.hint_free, false, 3, R.array.opt_home_quick);
        fNotes = k.text(R.string.lbl_notes, R.string.hint_free, false, 3);
    }

    private void loadForEdit(String id) {
        List<TreatmentProgram> list = TreatmentProgramManager.loadPrograms(this);
        for (TreatmentProgram t : list) {
            if (id.equals(t.id)) {
                fTitle.setText(t.title);
                fDiagnosis.setText(t.diagnosis);
                fGoals.setText(t.goals);
                fPhases.setText(t.phases);
                fExercises.setText(t.exercises);
                fEquipment.setText(t.equipment);
                fSessionsPlan.setText(t.sessionsPlan);
                fProgression.setText(t.progression);
                fFollowUp.setText(t.followUp);
                fHome.setText(t.homeProgram);
                fPrecautions.setText(t.precautions);
                fNotes.setText(t.notes);
                cAudience.set(t.audience);
                cRegion.setList(t.regions);
                cStage.set(t.stage);
                cModalities.setList(t.modalities);
                cDuration.set(t.sessionDuration);
                cOutcomes.setList(t.outcomes);
                sWeeks.set(t.durationWeeks);
                sPerWeek.set(t.sessionsPerWeek);
                return;
            }
        }
        Toast.makeText(this, R.string.program_not_found, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void submit() {
        String title = FormKit.textOf(fTitle);
        if (title.isEmpty()) {
            Toast.makeText(this, R.string.program_required_msg, Toast.LENGTH_SHORT).show();
            return;
        }

        TreatmentProgram t = new TreatmentProgram();
        t.title = title;
        t.diagnosis = FormKit.textOf(fDiagnosis);
        t.goals = FormKit.textOf(fGoals);
        t.phases = FormKit.textOf(fPhases);
        t.exercises = FormKit.textOf(fExercises);
        t.equipment = FormKit.textOf(fEquipment);
        t.sessionsPlan = FormKit.textOf(fSessionsPlan);
        t.progression = FormKit.textOf(fProgression);
        t.followUp = FormKit.textOf(fFollowUp);
        t.homeProgram = FormKit.textOf(fHome);
        t.precautions = FormKit.textOf(fPrecautions);
        t.notes = FormKit.textOf(fNotes);
        t.audience = cAudience.get();
        t.regions = cRegion.getList();
        t.stage = cStage.get();
        t.modalities = cModalities.getList();
        t.sessionDuration = cDuration.get();
        t.outcomes = cOutcomes.getList();
        t.durationWeeks = sWeeks.get();
        t.sessionsPerWeek = sPerWeek.get();

        if (editingId != null) {
            t.id = editingId;
            for (TreatmentProgram old : TreatmentProgramManager.loadPrograms(this)) {
                if (editingId.equals(old.id)) {
                    t.createdAt = old.createdAt;
                    break;
                }
            }
            TreatmentProgramManager.updateProgram(this, t);
            Toast.makeText(this, R.string.program_updated, Toast.LENGTH_SHORT).show();
        } else {
            TreatmentProgramManager.addProgram(this, t);
            Toast.makeText(this, R.string.program_saved, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        Motion.back(this);
    }
}
