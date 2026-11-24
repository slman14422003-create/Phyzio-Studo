package com.ast2012a.clinicalmaster;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CaseItem {
    public String id;
    public boolean custom;
    public List<String> keywords = new ArrayList<>();
    public String title = "";
    public String mode = "";
    public String freq = "";
    public String channel = "";
    public String duration = "";
    public List<String> poles = new ArrayList<>();
    public String explanation = "";
    public String symptoms = "";
    public String sessionsPlan = "";
    public String tip = "";

    public String category = "";
    public List<String> regions = new ArrayList<>();
    public String stage = "";
    public String severity = "";
    public String assessment = "";
    public String goals = "";
    public List<String> modalities = new ArrayList<>();
    public String dosage = "";
    public int sessionsPerWeek;
    public int totalSessions;
    public String homeExercises = "";
    public String precautions = "";
    public String progression = "";

    public static CaseItem fromJson(JSONObject o) {
        CaseItem c = new CaseItem();
        c.id = o.optString("id", null);
        c.custom = o.optBoolean("custom", false);
        c.keywords = listFromJson(o.optJSONArray("keywords"));
        c.title = o.optString("title", "");
        c.mode = o.optString("mode", "");
        c.freq = o.optString("freq", "");
        c.channel = o.optString("channel", "");
        c.duration = o.optString("duration", "");
        c.poles = listFromJson(o.optJSONArray("poles"));
        c.explanation = o.optString("explanation", "");
        c.symptoms = o.optString("symptoms", "");
        c.sessionsPlan = o.optString("sessionsPlan", "");
        c.tip = o.optString("tip", "");
        c.category = o.optString("category", "");
        c.regions = listFromJson(o.optJSONArray("regions"));
        c.stage = o.optString("stage", "");
        c.severity = o.optString("severity", "");
        c.assessment = o.optString("assessment", "");
        c.goals = o.optString("goals", "");
        c.modalities = listFromJson(o.optJSONArray("modalities"));
        c.dosage = o.optString("dosage", "");
        c.sessionsPerWeek = o.optInt("sessionsPerWeek", 0);
        c.totalSessions = o.optInt("totalSessions", 0);
        c.homeExercises = o.optString("homeExercises", "");
        c.precautions = o.optString("precautions", "");
        c.progression = o.optString("progression", "");
        return c;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        if (id != null) o.put("id", id);
        o.put("custom", custom);
        o.put("keywords", new JSONArray(keywords));
        o.put("title", title);
        o.put("mode", mode);
        o.put("freq", freq);
        o.put("channel", channel);
        o.put("duration", duration);
        o.put("poles", new JSONArray(poles));
        o.put("explanation", explanation);
        putIf(o, "symptoms", symptoms);
        putIf(o, "sessionsPlan", sessionsPlan);
        putIf(o, "tip", tip);
        putIf(o, "category", category);
        if (regions != null && !regions.isEmpty()) o.put("regions", new JSONArray(regions));
        putIf(o, "stage", stage);
        putIf(o, "severity", severity);
        putIf(o, "assessment", assessment);
        putIf(o, "goals", goals);
        if (modalities != null && !modalities.isEmpty()) o.put("modalities", new JSONArray(modalities));
        putIf(o, "dosage", dosage);
        if (sessionsPerWeek > 0) o.put("sessionsPerWeek", sessionsPerWeek);
        if (totalSessions > 0) o.put("totalSessions", totalSessions);
        putIf(o, "homeExercises", homeExercises);
        putIf(o, "precautions", precautions);
        putIf(o, "progression", progression);
        return o;
    }

    private static void putIf(JSONObject o, String key, String value) throws JSONException {
        if (value != null && !value.isEmpty()) o.put(key, value);
    }

    public static List<String> listFromJson(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.optString(i, ""));
        }
        return list;
    }

    public static String joinList(List<String> list) {
        StringBuilder sb = new StringBuilder();
        if (list == null) return "";
        for (String s : list) {
            if (s == null || s.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(s.trim());
        }
        return sb.toString();
    }

    public String searchBlob() {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append(' ').append(mode).append(' ').append(category).append(' ')
                .append(joinList(regions)).append(' ').append(stage).append(' ').append(severity).append(' ')
                .append(assessment).append(' ').append(goals).append(' ').append(joinList(modalities)).append(' ')
                .append(dosage).append(' ').append(homeExercises).append(' ').append(precautions).append(' ')
                .append(progression).append(' ').append(symptoms);
        return sb.toString();
    }

    public String toShareText(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n");
        line(sb, ctx.getString(R.string.lbl_category), category);
        line(sb, ctx.getString(R.string.lbl_region), joinList(regions));
        line(sb, ctx.getString(R.string.lbl_stage), stage);
        line(sb, ctx.getString(R.string.lbl_severity), severity);
        line(sb, ctx.getString(R.string.lbl_modalities), joinList(modalities));
        if (sessionsPerWeek > 0) {
            line(sb, ctx.getString(R.string.lbl_sessions_week), ctx.getString(R.string.fmt_per_week, sessionsPerWeek));
        }
        if (totalSessions > 0) {
            line(sb, ctx.getString(R.string.lbl_sessions_total), ctx.getString(R.string.fmt_total, totalSessions));
        }
        line(sb, ctx.getString(R.string.lbl_session_duration), duration);
        block(sb, ctx.getString(R.string.lbl_explanation), explanation);
        block(sb, ctx.getString(R.string.lbl_symptoms), symptoms);
        block(sb, ctx.getString(R.string.lbl_assessment), assessment);
        block(sb, ctx.getString(R.string.lbl_goals), goals);
        block(sb, ctx.getString(R.string.lbl_dosage), dosage);
        block(sb, ctx.getString(R.string.lbl_sessions_plan), sessionsPlan);
        block(sb, ctx.getString(R.string.lbl_home), homeExercises);
        block(sb, ctx.getString(R.string.lbl_precautions), precautions);
        block(sb, ctx.getString(R.string.lbl_progression), progression);
        block(sb, ctx.getString(R.string.lbl_tip), tip);
        return sb.toString().trim();
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
