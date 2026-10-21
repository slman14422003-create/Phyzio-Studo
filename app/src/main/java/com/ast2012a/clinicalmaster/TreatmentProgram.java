package com.ast2012a.clinicalmaster;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TreatmentProgram {
    public String id;
    public String title = "";
    public String diagnosis = "";
    public String goals = "";
    public String phases = "";
    public String exercises = "";
    public String precautions = "";
    public String sessionsPlan = "";
    public String notes = "";
    public long createdAt;

    public String audience = "";
    public List<String> regions = new ArrayList<>();
    public String stage = "";
    public int durationWeeks;
    public int sessionsPerWeek;
    public String sessionDuration = "";
    public List<String> modalities = new ArrayList<>();
    public String equipment = "";
    public List<String> outcomes = new ArrayList<>();
    public String progression = "";
    public String homeProgram = "";
    public String followUp = "";

    public static TreatmentProgram fromJson(JSONObject o) {
        TreatmentProgram t = new TreatmentProgram();
        t.id = o.optString("id", "");
        t.title = o.optString("title", "");
        t.diagnosis = o.optString("diagnosis", "");
        t.goals = o.optString("goals", "");
        t.phases = o.optString("phases", "");
        t.exercises = o.optString("exercises", "");
        t.precautions = o.optString("precautions", "");
        t.sessionsPlan = o.optString("sessionsPlan", "");
        t.notes = o.optString("notes", "");
        t.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        t.audience = o.optString("audience", "");
        t.regions = CaseItem.listFromJson(o.optJSONArray("regions"));
        t.stage = o.optString("stage", "");
        t.durationWeeks = o.optInt("durationWeeks", 0);
        t.sessionsPerWeek = o.optInt("sessionsPerWeek", 0);
        t.sessionDuration = o.optString("sessionDuration", "");
        t.modalities = CaseItem.listFromJson(o.optJSONArray("modalities"));
        t.equipment = o.optString("equipment", "");
        t.outcomes = CaseItem.listFromJson(o.optJSONArray("outcomes"));
        t.progression = o.optString("progression", "");
        t.homeProgram = o.optString("homeProgram", "");
        t.followUp = o.optString("followUp", "");
        return t;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("diagnosis", diagnosis);
        o.put("goals", goals);
        o.put("phases", phases);
        o.put("exercises", exercises);
        o.put("precautions", precautions);
        o.put("sessionsPlan", sessionsPlan);
        o.put("notes", notes);
        o.put("createdAt", createdAt);
        o.put("audience", audience);
        o.put("regions", new JSONArray(regions));
        o.put("stage", stage);
        o.put("durationWeeks", durationWeeks);
        o.put("sessionsPerWeek", sessionsPerWeek);
        o.put("sessionDuration", sessionDuration);
        o.put("modalities", new JSONArray(modalities));
        o.put("equipment", equipment);
        o.put("outcomes", new JSONArray(outcomes));
        o.put("progression", progression);
        o.put("homeProgram", homeProgram);
        o.put("followUp", followUp);
        return o;
    }

    public String toShareText(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(title == null || title.trim().isEmpty() ? ctx.getString(R.string.program_default_title) : title.trim()).append('\n');
        line(sb, ctx.getString(R.string.lbl_diagnosis), diagnosis);
        line(sb, ctx.getString(R.string.lbl_audience), audience);
        line(sb, ctx.getString(R.string.lbl_region), CaseItem.joinList(regions));
        line(sb, ctx.getString(R.string.lbl_stage), stage);
        if (durationWeeks > 0) line(sb, ctx.getString(R.string.lbl_weeks), ctx.getString(R.string.fmt_weeks, durationWeeks));
        if (sessionsPerWeek > 0) line(sb, ctx.getString(R.string.lbl_sessions_week), ctx.getString(R.string.fmt_per_week, sessionsPerWeek));
        line(sb, ctx.getString(R.string.lbl_session_duration), sessionDuration);
        line(sb, ctx.getString(R.string.lbl_modalities), CaseItem.joinList(modalities));
        line(sb, ctx.getString(R.string.lbl_outcomes), CaseItem.joinList(outcomes));
        block(sb, ctx.getString(R.string.lbl_goals), goals);
        block(sb, ctx.getString(R.string.lbl_phases), phases);
        block(sb, ctx.getString(R.string.lbl_exercises), exercises);
        block(sb, ctx.getString(R.string.lbl_equipment), equipment);
        block(sb, ctx.getString(R.string.lbl_sessions_plan), sessionsPlan);
        block(sb, ctx.getString(R.string.lbl_progression), progression);
        block(sb, ctx.getString(R.string.lbl_followup), followUp);
        block(sb, ctx.getString(R.string.lbl_home), homeProgram);
        block(sb, ctx.getString(R.string.lbl_precautions), precautions);
        block(sb, ctx.getString(R.string.lbl_notes), notes);
        return sb.toString().trim();
    }

    public String searchBlob() {
        return title + " " + diagnosis + " " + goals + " " + audience + " " + stage + " "
                + CaseItem.joinList(regions) + " " + CaseItem.joinList(modalities) + " "
                + CaseItem.joinList(outcomes) + " " + phases + " " + exercises;
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        sb.append("• ").append(label).append(": ").append(value.trim()).append("\n");
    }

    private static void block(StringBuilder sb, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        sb.append("\n").append(label).append(":\n").append(value.trim()).append("\n");
    }
}
