package com.ast2012a.clinicalmaster;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DeepEvidence {

    static final int MAX_QUERIES = 4;
    static final int MAX_ARTICLES = 8;
    static final int PER_QUERY = 5;
    static final int ABSTRACT_CHARS = 1400;

    private static final Pattern CITATION = Pattern.compile("\\[\\s*(\\d{1,3}(?:\\s*[,\\u060C\\-\\u2013]\\s*\\d{1,3})*)\\s*\\]");

    private DeepEvidence() {}

    static List<String> parsePlan(String raw, String fallbackTerm) {
        List<String> queries = new ArrayList<>();
        if (raw != null) {
            for (String line : raw.replace("\r", "").split("\n")) {
                String t = line.trim();
                if (t.length() < 3) continue;
                if (!t.regionMatches(true, 0, "Q:", 0, 2)) continue;
                String q = t.substring(2).trim();
                q = q.replaceAll("^[\"'`]+|[\"'`]+$", "").trim();
                if (q.length() < 4 || q.length() > 220) continue;
                boolean dup = false;
                for (String existing : queries) {
                    if (existing.equalsIgnoreCase(q)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) queries.add(q);
                if (queries.size() >= MAX_QUERIES) break;
            }
        }
        if (queries.isEmpty() && fallbackTerm != null && !fallbackTerm.trim().isEmpty()) {
            String term = fallbackTerm.trim();
            queries.add(term + " AND (systematic review OR meta-analysis OR randomized controlled trial)");
            queries.add(term);
        }
        return queries;
    }

    static List<PubMedXml.Article> merge(List<List<PubMedXml.Article>> groups, int max) {
        List<PubMedXml.Article> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (List<PubMedXml.Article> group : groups) {
            if (group == null) continue;
            for (PubMedXml.Article a : group) {
                if (a == null || a.pmid.isEmpty()) continue;
                if (seen.add(a.pmid)) merged.add(a);
            }
        }
        PubMedXml.rank(merged);
        if (merged.size() > max) return new ArrayList<>(merged.subList(0, max));
        return merged;
    }

    static String buildReferencesContext(List<PubMedXml.Article> articles) {
        if (articles == null || articles.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("PubMed references (numbered; cite them only by these numbers):\n");
        for (int i = 0; i < articles.size(); i++) {
            PubMedXml.Article a = articles.get(i);
            sb.append("\nReference [").append(i + 1).append("] - PubMed, ").append(a.evidenceLabel());
            if (!a.year.isEmpty()) sb.append(", ").append(a.year);
            if (!a.journal.isEmpty()) sb.append(", ").append(a.journal);
            sb.append("\nTitle: ").append(a.title).append("\nAbstract: ");
            String abs = a.abstractText;
            if (abs.length() > ABSTRACT_CHARS) abs = abs.substring(0, ABSTRACT_CHARS) + "...";
            sb.append(abs).append('\n');
        }
        return sb.toString();
    }

    static String finalizeCitations(String answer, List<PubMedXml.Article> articles, String heading) {
        if (answer == null || answer.isEmpty()) return answer;
        int count = articles == null ? 0 : articles.size();
        Set<Integer> used = new LinkedHashSet<>();
        StringBuffer cleaned = new StringBuffer();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            List<Integer> valid = new ArrayList<>();
            for (int n : expand(m.group(1))) {
                if (n >= 1 && n <= count && !valid.contains(n)) valid.add(n);
            }
            String replacement;
            if (valid.isEmpty()) {
                replacement = "";
            } else {
                StringBuilder b = new StringBuilder("[");
                for (int i = 0; i < valid.size(); i++) {
                    if (i > 0) b.append(',');
                    b.append(valid.get(i));
                    used.add(valid.get(i));
                }
                replacement = b.append(']').toString();
            }
            m.appendReplacement(cleaned, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(cleaned);
        String body = cleaned.toString().replaceAll("[ \\t]+([.,;:\\u060C\\u061B])", "$1")
                .replaceAll("(?<=\\S)[ \\t]{2,}(?=\\S)", " ").trim();
        if (used.isEmpty()) return body;

        List<Integer> order = new ArrayList<>(used);
        java.util.Collections.sort(order);
        StringBuilder sb = new StringBuilder(body);
        sb.append("\n\n## ").append(heading).append('\n');
        for (int n : order) {
            PubMedXml.Article a = articles.get(n - 1);
            sb.append("- **[").append(n).append("]** ").append(a.title);
            if (!a.journal.isEmpty() || !a.year.isEmpty()) {
                sb.append(' ').append(a.journal);
                if (!a.year.isEmpty()) sb.append(a.journal.isEmpty() ? "" : ", ").append(a.year);
                sb.append('.');
            }
            sb.append(" PMID ").append(a.pmid).append(" - ").append(a.url()).append('\n');
        }
        return sb.toString().trim();
    }

    private static List<Integer> expand(String group) {
        List<Integer> out = new ArrayList<>();
        String norm = group.replace('\u060C', ',').replace('\u2013', '-').replaceAll("\\s+", "");
        for (String part : norm.split(",")) {
            if (part.isEmpty()) continue;
            int dash = part.indexOf('-');
            try {
                if (dash > 0) {
                    int from = Integer.parseInt(part.substring(0, dash));
                    int to = Integer.parseInt(part.substring(dash + 1));
                    if (to - from > 12 || to < from) continue;
                    for (int n = from; n <= to; n++) out.add(n);
                } else {
                    out.add(Integer.parseInt(part));
                }
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return out;
    }
}
