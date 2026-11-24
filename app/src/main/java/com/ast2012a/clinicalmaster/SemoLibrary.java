package com.ast2012a.clinicalmaster;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SemoLibrary {
    private SemoLibrary() {}

    private static final String FILE = "semo_library.json";
    private static final Object LOCK = new Object();

    public static final class Unanswered {
        public String question = "";
        public int count = 0;
        public long lastAsked = 0L;
    }

    private static final class Store {
        final List<SemoCard> cards = new ArrayList<>();
        final List<Unanswered> unanswered = new ArrayList<>();
    }

    private static Store read(Context ctx) {
        Store s = new Store();
        try {
            if (!ctx.getFileStreamPath(FILE).exists()) return s;
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    ctx.openFileInput(FILE), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            reader.close();
            JSONObject root = new JSONObject(sb.toString());
            JSONArray cards = root.optJSONArray("cards");
            if (cards != null) {
                for (int i = 0; i < cards.length(); i++) s.cards.add(cardFromJson(cards.getJSONObject(i)));
            }
            JSONArray un = root.optJSONArray("unanswered");
            if (un != null) {
                for (int i = 0; i < un.length(); i++) {
                    JSONObject o = un.getJSONObject(i);
                    Unanswered u = new Unanswered();
                    u.question = o.optString("question", "");
                    u.count = o.optInt("count", 1);
                    u.lastAsked = o.optLong("lastAsked", 0L);
                    s.unanswered.add(u);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }

    private static void write(Context ctx, Store s) {
        try {
            JSONArray cards = new JSONArray();
            for (SemoCard c : s.cards) cards.put(cardToJson(c));
            JSONArray un = new JSONArray();
            for (Unanswered u : s.unanswered) {
                JSONObject o = new JSONObject();
                o.put("question", u.question);
                o.put("count", u.count);
                o.put("lastAsked", u.lastAsked);
                un.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("cards", cards);
            root.put("unanswered", un);
            FileOutputStream fos = ctx.openFileOutput(FILE, Context.MODE_PRIVATE);
            fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JSONObject cardToJson(SemoCard c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", c.id);
        o.put("question", c.question);
        o.put("keywords", new JSONArray(c.keywords));
        o.put("answer", c.answer);
        o.put("source", c.source);
        o.put("status", c.status.name());
        o.put("approvedAt", c.approvedAt);
        o.put("createdAt", c.createdAt);
        JSONArray h = new JSONArray();
        for (SemoCard.Version v : c.history) {
            JSONObject ho = new JSONObject();
            ho.put("answer", v.answer);
            ho.put("timestamp", v.timestamp);
            h.put(ho);
        }
        o.put("history", h);
        return o;
    }

    private static SemoCard cardFromJson(JSONObject o) {
        SemoCard c = new SemoCard();
        c.id = o.optString("id", UUID.randomUUID().toString().substring(0, 12));
        c.question = o.optString("question", "");
        JSONArray k = o.optJSONArray("keywords");
        if (k != null) {
            for (int i = 0; i < k.length(); i++) {
                String w = k.optString(i, "").trim();
                if (!w.isEmpty()) c.keywords.add(w);
            }
        }
        c.answer = o.optString("answer", "");
        c.source = o.optString("source", "");
        c.status = "APPROVED".equals(o.optString("status")) ? SemoCard.Status.APPROVED : SemoCard.Status.DRAFT;
        c.approvedAt = o.optLong("approvedAt", 0L);
        c.createdAt = o.optLong("createdAt", 0L);
        JSONArray h = o.optJSONArray("history");
        if (h != null) {
            for (int i = 0; i < h.length(); i++) {
                JSONObject ho = h.optJSONObject(i);
                if (ho != null) c.history.add(new SemoCard.Version(ho.optString("answer", ""), ho.optLong("timestamp", 0L)));
            }
        }
        if (c.status == SemoCard.Status.APPROVED
                && (c.question.trim().isEmpty() || c.answer.trim().isEmpty())) {
            c.status = SemoCard.Status.DRAFT;
            c.approvedAt = 0L;
        }
        return c;
    }

    public static List<SemoCard> load(Context ctx) {
        synchronized (LOCK) {
            return read(ctx).cards;
        }
    }

    public static List<SemoCard> list(Context ctx, SemoCard.Status status) {
        List<SemoCard> out = new ArrayList<>();
        for (SemoCard c : load(ctx)) if (c.status == status) out.add(c);
        return out;
    }

    public static SemoCard get(Context ctx, String id) {
        if (id == null) return null;
        for (SemoCard c : load(ctx)) if (id.equals(c.id)) return c;
        return null;
    }

    public static SemoCard add(Context ctx, SemoCard card) {
        synchronized (LOCK) {
            Store s = read(ctx);
            SemoCard c = card.copy();
            c.id = UUID.randomUUID().toString().substring(0, 12);
            c.createdAt = System.currentTimeMillis();
            c.status = SemoCard.Status.DRAFT;
            c.approvedAt = 0L;
            c.history = new ArrayList<>();
            s.cards.add(c);
            write(ctx, s);
            return c;
        }
    }

    public static int addDraftsSkippingExisting(Context ctx, List<SemoCard> drafts) {
        synchronized (LOCK) {
            Store s = read(ctx);
            Set<String> seen = new HashSet<>();
            for (SemoCard c : s.cards) seen.add(SemoSearch.normalize(c.answer));
            int added = 0;
            long now = System.currentTimeMillis();
            for (SemoCard d : drafts) {
                String key = SemoSearch.normalize(d.answer);
                if (key.isEmpty() || !seen.add(key)) continue;
                SemoCard c = d.copy();
                c.id = UUID.randomUUID().toString().substring(0, 12);
                c.createdAt = now;
                c.status = SemoCard.Status.DRAFT;
                c.approvedAt = 0L;
                c.history = new ArrayList<>();
                s.cards.add(c);
                added++;
            }
            if (added > 0) write(ctx, s);
            return added;
        }
    }

    public static boolean update(Context ctx, SemoCard edited) {
        synchronized (LOCK) {
            Store s = read(ctx);
            for (int i = 0; i < s.cards.size(); i++) {
                SemoCard old = s.cards.get(i);
                if (!old.id.equals(edited.id)) continue;
                SemoCard c = old.copy();
                boolean answerChanged = !old.answer.equals(edited.answer);
                boolean changed = answerChanged
                        || !old.question.equals(edited.question)
                        || !old.source.equals(edited.source)
                        || !old.keywords.equals(edited.keywords);
                if (old.isApproved() && changed) {
                    if (answerChanged) c.history.add(new SemoCard.Version(old.answer, System.currentTimeMillis()));
                    c.status = SemoCard.Status.DRAFT;
                    c.approvedAt = 0L;
                }
                c.question = edited.question;
                c.keywords = new ArrayList<>(edited.keywords);
                c.answer = edited.answer;
                c.source = edited.source;
                s.cards.set(i, c);
                write(ctx, s);
                return true;
            }
            return false;
        }
    }

    public static boolean approve(Context ctx, String id) {
        synchronized (LOCK) {
            Store s = read(ctx);
            for (SemoCard c : s.cards) {
                if (!c.id.equals(id)) continue;
                if (c.question.trim().isEmpty() || c.answer.trim().isEmpty()) return false;
                if (!c.isApproved()) {
                    c.status = SemoCard.Status.APPROVED;
                    c.approvedAt = System.currentTimeMillis();
                }
                String key = SemoSearch.normalize(c.question);
                for (int i = s.unanswered.size() - 1; i >= 0; i--) {
                    if (SemoSearch.normalize(s.unanswered.get(i).question).equals(key)) s.unanswered.remove(i);
                }
                write(ctx, s);
                return true;
            }
            return false;
        }
    }

    public static void delete(Context ctx, String id) {
        synchronized (LOCK) {
            Store s = read(ctx);
            for (int i = s.cards.size() - 1; i >= 0; i--) if (s.cards.get(i).id.equals(id)) s.cards.remove(i);
            write(ctx, s);
        }
    }

    public static SemoSearch.Result findApproved(Context ctx, String query) {
        return SemoSearch.search(load(ctx), query);
    }

    public static void recordUnanswered(Context ctx, String question) {
        if (question == null || question.trim().isEmpty()) return;
        synchronized (LOCK) {
            Store s = read(ctx);
            String key = SemoSearch.normalize(question);
            long now = System.currentTimeMillis();
            for (Unanswered u : s.unanswered) {
                if (SemoSearch.normalize(u.question).equals(key)) {
                    u.count++;
                    u.lastAsked = now;
                    write(ctx, s);
                    return;
                }
            }
            Unanswered u = new Unanswered();
            u.question = question.trim();
            u.count = 1;
            u.lastAsked = now;
            s.unanswered.add(u);
            write(ctx, s);
        }
    }

    public static List<Unanswered> listUnanswered(Context ctx) {
        synchronized (LOCK) {
            List<Unanswered> list = read(ctx).unanswered;
            list.sort((a, b) -> Long.compare(b.lastAsked, a.lastAsked));
            return list;
        }
    }

    public static void removeUnanswered(Context ctx, String question) {
        synchronized (LOCK) {
            Store s = read(ctx);
            String key = SemoSearch.normalize(question);
            for (int i = s.unanswered.size() - 1; i >= 0; i--) {
                if (SemoSearch.normalize(s.unanswered.get(i).question).equals(key)) s.unanswered.remove(i);
            }
            write(ctx, s);
        }
    }

    public static final int CHUNK_MIN = 300;
    public static final int CHUNK_MAX = 600;

    public static List<String> chunk(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        List<String> parts = new ArrayList<>();
        for (String para : text.replace("\f", "\n\n").split("\\n\\s*\\n")) {
            String p = para.replaceAll("\\s+", " ").trim();
            while (p.length() > CHUNK_MAX) {
                int cut = -1;
                for (int i = CHUNK_MAX; i >= CHUNK_MIN; i--) {
                    char ch = p.charAt(i - 1);
                    if (ch == '.' || ch == '\u061F' || ch == '?' || ch == '!' || ch == '\u061B') {
                        cut = i;
                        break;
                    }
                }
                if (cut < 0) {
                    for (int i = CHUNK_MAX; i >= CHUNK_MIN; i--) {
                        if (p.charAt(i - 1) == ' ') {
                            cut = i;
                            break;
                        }
                    }
                }
                if (cut < 0) cut = CHUNK_MAX;
                parts.add(p.substring(0, cut).trim());
                p = p.substring(cut).trim();
            }
            if (!p.isEmpty()) parts.add(p);
        }
        StringBuilder cur = new StringBuilder();
        for (String p : parts) {
            if (cur.length() > 0 && cur.length() + 1 + p.length() > CHUNK_MAX) {
                if (cur.length() >= CHUNK_MIN) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            }
            if (cur.length() > 0) cur.append("\n\n");
            cur.append(p);
            if (cur.length() >= CHUNK_MIN) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() >= 100) out.add(cur.toString());
        return out;
    }
}
