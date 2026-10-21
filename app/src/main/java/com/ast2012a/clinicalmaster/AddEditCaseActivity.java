package com.ast2012a.clinicalmaster;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

public class AddEditCaseActivity extends AppCompatActivity {

    private EditText fKeywords, fTitle, fExplanation, fSymptoms, fAssessment, fGoals, fDosage,
            fSessionsPlan, fHome, fPrecautions, fProgression, fTip;
    private FormKit.Choice cCategory, cRegion, cStage, cSeverity, cModalities, cDuration;
    private FormKit.Stepper sWeek, sTotal;
    private MaterialToolbar toolbar;
    private TextView submitBtn;

    private String editingCaseId = null;
    private CaseItem existing = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        buildForm();

        submitBtn = findViewById(R.id.btn_submit);
        TextView cancelBtn = findViewById(R.id.btn_cancel);
        submitBtn.setOnClickListener(v -> submit());
        cancelBtn.setOnClickListener(v -> finish());

        editingCaseId = getIntent().getStringExtra("edit_case_id");
        if (editingCaseId != null) {
            loadForEdit(editingCaseId);
        } else {
            applyPrefillFromAi();
        }
    }

    private void buildForm() {
        LinearLayout root = findViewById(R.id.form_root);
        FormKit k = new FormKit(this, root);
        k.banner(IllustrationView.SPINE, 124);
        k.intro(R.string.case_form_intro);

        k.section(R.string.sec_basic);
        fKeywords = k.text(R.string.lbl_keywords, R.string.hint_keywords, true, 1);
        fTitle = k.text(R.string.lbl_case_title, 0, true, 1);
        fExplanation = k.text(R.string.lbl_explanation, R.string.hint_explanation, true, 4);

        k.section(R.string.sec_classification);
        cCategory = k.single(R.string.lbl_category, R.array.opt_category);
        cRegion = k.multi(R.string.lbl_region, R.array.opt_region);
        cStage = k.single(R.string.lbl_stage, R.array.opt_stage);
        cSeverity = k.single(R.string.lbl_severity, R.array.opt_severity);

        k.section(R.string.sec_assessment);
        fSymptoms = k.textWithQuick(R.string.lbl_symptoms, R.string.hint_free, false, 3, R.array.opt_symptoms_quick);
        fAssessment = k.textWithQuick(R.string.lbl_assessment, R.string.hint_free, false, 3, R.array.opt_assessment_quick);

        k.section(R.string.sec_treatment);
        fGoals = k.textWithQuick(R.string.lbl_goals, R.string.hint_free, false, 3, R.array.opt_goals_quick);
        cModalities = k.multi(R.string.lbl_modalities, R.array.opt_modalities);
        fDosage = k.text(R.string.lbl_dosage, R.string.hint_dosage, false, 3);

        k.section(R.string.sec_dosage);
        sWeek = k.stepper(R.string.lbl_sessions_week, R.string.unit_per_week, 7);
        sTotal = k.stepper(R.string.lbl_sessions_total, R.string.unit_sessions, 60);
        cDuration = k.single(R.string.lbl_session_duration, R.array.opt_duration);
        fSessionsPlan = k.text(R.string.lbl_sessions_plan, R.string.hint_free, false, 3);

        k.section(R.string.sec_home);
        fHome = k.textWithQuick(R.string.lbl_home, R.string.hint_free, false, 3, R.array.opt_home_quick);
        fProgression = k.textWithQuick(R.string.lbl_progression, R.string.hint_free, false, 3, R.array.opt_progression_quick);
        fTip = k.text(R.string.lbl_tip, R.string.hint_free, false, 2);

        k.section(R.string.sec_safety);
        fPrecautions = k.textWithQuick(R.string.lbl_precautions, R.string.hint_free, false, 3, R.array.opt_precautions_quick);
    }

    private void applyPrefillFromAi() {
        String prefillExplanation = getIntent().getStringExtra("prefill_explanation");
        if (prefillExplanation != null && !prefillExplanation.isEmpty()) {
            fExplanation.setText(prefillExplanation);
            Toast.makeText(this, R.string.case_prefill_hint, Toast.LENGTH_LONG).show();
        }
    }

    private void loadForEdit(String id) {
        CaseItem item = null;
        for (CaseItem c : DataManager.loadCustomCases(this)) {
            if (id.equals(c.id)) { item = c; break; }
        }
        if (item == null) {
            Toast.makeText(this, R.string.case_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        existing = item;

        toolbar.setTitle(R.string.case_form_edit);
        submitBtn.setText(R.string.form_save_changes);

        fKeywords.setText(String.join(", ", item.keywords));
        fTitle.setText(item.title);
        fExplanation.setText(item.explanation);
        fSymptoms.setText(item.symptoms);
        fAssessment.setText(item.assessment);
        fGoals.setText(item.goals);
        fDosage.setText(item.dosage);
        fSessionsPlan.setText(item.sessionsPlan);
        fHome.setText(item.homeExercises);
        fProgression.setText(item.progression);
        fTip.setText(item.tip);
        fPrecautions.setText(item.precautions);
        cCategory.set(item.category);
        cRegion.setList(item.regions);
        cStage.set(item.stage);
        cSeverity.set(item.severity);
        cModalities.setList(item.modalities);
        cDuration.set(item.duration);
        sWeek.set(item.sessionsPerWeek);
        sTotal.set(item.totalSessions);
    }

    private void submit() {
        String title = FormKit.textOf(fTitle);
        String explanation = FormKit.textOf(fExplanation);
        String keywordsRaw = FormKit.textOf(fKeywords);

        if (title.isEmpty() || explanation.isEmpty() || keywordsRaw.isEmpty()) {
            Toast.makeText(this, R.string.case_required_msg, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> keywords = new ArrayList<>();
        for (String k : keywordsRaw.split("[,،]")) {
            String t = k.trim();
            if (!t.isEmpty()) keywords.add(t);
        }

        CaseItem f = new CaseItem();
        f.keywords = keywords;
        f.title = title;
        f.explanation = explanation;
        f.symptoms = FormKit.textOf(fSymptoms);
        f.assessment = FormKit.textOf(fAssessment);
        f.goals = FormKit.textOf(fGoals);
        f.dosage = FormKit.textOf(fDosage);
        f.sessionsPlan = FormKit.textOf(fSessionsPlan);
        f.homeExercises = FormKit.textOf(fHome);
        f.progression = FormKit.textOf(fProgression);
        f.tip = FormKit.textOf(fTip);
        f.precautions = FormKit.textOf(fPrecautions);
        f.category = cCategory.get();
        f.regions = cRegion.getList();
        f.stage = cStage.get();
        f.severity = cSeverity.get();
        f.modalities = cModalities.getList();
        f.sessionsPerWeek = sWeek.get();
        f.totalSessions = sTotal.get();
        String duration = cDuration.get();
        f.duration = duration;
        f.mode = f.category;

        if (existing != null) {
            if (existing.mode != null && !existing.mode.isEmpty()) f.mode = existing.mode;
            f.freq = existing.freq;
            f.channel = existing.channel;
            f.poles = existing.poles;
            if (duration.isEmpty()) f.duration = existing.duration;
        }

        if (editingCaseId != null) {
            DataManager.updateCustomCase(this, editingCaseId, f);
            Toast.makeText(this, R.string.case_updated, Toast.LENGTH_SHORT).show();
        } else {
            DataManager.addCustomCase(this, f);
            Toast.makeText(this, R.string.case_saved, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down_out);
    }
}
