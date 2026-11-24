package com.ast2012a.clinicalmaster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SemoSearch {
    private SemoSearch() {}

    public static final double MIN_SCORE = 3.0;
    public static final double MIN_COVERAGE = 0.5;

    public static final double W_QUESTION = 3.0;
    public static final double W_KEYWORDS = 2.0;
    public static final double W_ANSWER = 1.0;

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    public static final class Result {
        public final SemoCard card;
        public final double score;

        Result(SemoCard card, double score) {
            this.card = card;
            this.score = score;
        }
    }

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "ما", "ماذا", "هو", "هي", "هل", "كيف", "متي", "لماذا", "اين", "في", "من", "علي", "الي",
            "عن", "مع", "او", "ان", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي", "يا", "لا", "كل",
            "the", "a", "an", "is", "are", "of", "to", "in", "on", "for", "and", "or", "what", "how",
            "when", "why", "which", "with", "do", "does", "can", "i", "it", "by"));

    private static final String[] PREFIX_LONG = {"وال", "بال", "كال", "فال", "لل", "ال"};
    private static final String[] PREFIX_SHORT = {"و", "ف", "ب", "ل"};
    private static final String[] SUFFIX = {"تها", "تهم", "ات", "ان", "ون", "ين", "ها", "هم", "هن", "كم",
            "كن", "نا", "وا", "ته", "تي", "تك", "ه", "ي"};
    private static final String[] EN_SUFFIX = {"ing", "ed", "es", "s"};

    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0x0640 || (c >= 0x064B && c <= 0x065F) || c == 0x0670
                    || (c >= 0x06D6 && c <= 0x06ED) || (c >= 0x200B && c <= 0x200F)
                    || (c >= 0x202A && c <= 0x202E) || c == 0xFEFF) {
                continue;
            }
            switch (c) {
                case '\u0623':
                case '\u0625':
                case '\u0622':
                case '\u0671':
                    sb.append('\u0627');
                    continue;
                case '\u0624':
                    sb.append('\u0648');
                    continue;
                case '\u0626':
                case '\u0649':
                case '\u06CC':
                    sb.append('\u064A');
                    continue;
                case '\u0621':
                    continue;
                case '\u0629':
                    sb.append('\u0647');
                    continue;
                case '\u06A9':
                    sb.append('\u0643');
                    continue;
                default:
                    break;
            }
            if (c >= 0x0660 && c <= 0x0669) {
                sb.append((char) ('0' + (c - 0x0660)));
            } else if (c >= 0x06F0 && c <= 0x06F9) {
                sb.append((char) ('0' + (c - 0x06F0)));
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(' ');
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    public static String stem(String token) {
        if (token == null || token.isEmpty()) return "";
        if (!isArabic(token)) {
            for (String suf : EN_SUFFIX) {
                if (token.endsWith(suf) && token.length() - suf.length() >= 3) {
                    return token.substring(0, token.length() - suf.length());
                }
            }
            return token;
        }
        String t = token;
        boolean cut = false;
        for (String p : PREFIX_LONG) {
            if (t.startsWith(p) && t.length() - p.length() >= 3) {
                t = t.substring(p.length());
                cut = true;
                break;
            }
        }
        if (!cut) {
            for (String p : PREFIX_SHORT) {
                if (t.startsWith(p) && t.length() - p.length() >= 4) {
                    t = t.substring(p.length());
                    break;
                }
            }
        }
        for (String suf : SUFFIX) {
            if (t.endsWith(suf) && t.length() - suf.length() >= 3) {
                t = t.substring(0, t.length() - suf.length());
                break;
            }
        }
        return t;
    }

    public static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        String n = normalize(text);
        if (n.isEmpty()) return out;
        for (String raw : n.split(" ")) {
            if (raw.isEmpty() || STOP.contains(raw)) continue;
            String st = stem(raw);
            if (st.length() < 2 && !isDigits(st)) continue;
            out.add(st);
        }
        return out;
    }

    private static boolean isArabic(String t) {
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) return true;
        }
        return false;
    }

    private static boolean isDigits(String t) {
        if (t.isEmpty()) return false;
        for (int i = 0; i < t.length(); i++) if (!Character.isDigit(t.charAt(i))) return false;
        return true;
    }

    private static final class Doc {
        final SemoCard card;
        final List<String> q;
        final List<String> k;
        final List<String> a;

        Doc(SemoCard card) {
            this.card = card;
            this.q = tokenize(card.question);
            StringBuilder kw = new StringBuilder();
            for (String s : card.keywords) kw.append(s).append(' ');
            this.k = tokenize(kw.toString());
            this.a = tokenize(card.answer);
        }

        List<String> field(int f) {
            return f == 0 ? q : f == 1 ? k : a;
        }
    }

    public static Result search(List<SemoCard> cards, String query) {
        if (cards == null || query == null) return null;
        LinkedHashSet<String> qset = new LinkedHashSet<>(tokenize(query));
        if (qset.isEmpty()) return null;

        List<Doc> docs = new ArrayList<>();
        for (SemoCard c : cards) {
            if (c != null && c.status == SemoCard.Status.APPROVED) docs.add(new Doc(c));
        }
        if (docs.isEmpty()) return null;

        final int n = docs.size();
        final double[] weights = {W_QUESTION, W_KEYWORDS, W_ANSWER};
        double[] avgLen = new double[3];
        List<Map<String, Integer>> df = new ArrayList<>();
        for (int f = 0; f < 3; f++) {
            Map<String, Integer> m = new HashMap<>();
            long total = 0;
            for (Doc d : docs) {
                List<String> toks = d.field(f);
                total += toks.size();
                for (String t : new HashSet<>(toks)) {
                    Integer cur = m.get(t);
                    m.put(t, cur == null ? 1 : cur + 1);
                }
            }
            avgLen[f] = Math.max(1.0, (double) total / n);
            df.add(m);
        }

        Doc best = null;
        double bestScore = 0;
        for (Doc d : docs) {
            double score = 0;
            Set<String> matchedQK = new HashSet<>();
            for (int f = 0; f < 3; f++) {
                List<String> toks = d.field(f);
                if (toks.isEmpty()) continue;
                Map<String, Integer> tf = new HashMap<>();
                for (String t : toks) {
                    Integer cur = tf.get(t);
                    tf.put(t, cur == null ? 1 : cur + 1);
                }
                double norm = K1 * (1 - B + B * toks.size() / avgLen[f]);
                for (String qt : qset) {
                    Integer freq = tf.get(qt);
                    if (freq == null) continue;
                    Integer dfv = df.get(f).get(qt);
                    int dfi = dfv == null ? 0 : dfv;
                    double idf = Math.log(1.0 + (n + 1.0) / (dfi + 0.5));
                    score += weights[f] * idf * (freq * (K1 + 1)) / (freq + norm);
                    if (f < 2) matchedQK.add(qt);
                }
            }
            if (matchedQK.isEmpty()) continue;
            if ((double) matchedQK.size() / qset.size() < MIN_COVERAGE) continue;
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        if (best == null || bestScore <= MIN_SCORE) return null;
        return new Result(best.card, bestScore);
    }
}
