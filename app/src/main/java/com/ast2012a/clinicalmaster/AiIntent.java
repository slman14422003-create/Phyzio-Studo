package com.ast2012a.clinicalmaster;

import java.util.Locale;

final class AiIntent {

    static final int ROUTE_CHAT = 0;
    static final int ROUTE_SEARCH = 1;

    static final int DEPTH_QUICK = 0;
    static final int DEPTH_STANDARD = 1;
    static final int DEPTH_DEEP = 2;

    final int route;
    final int depth;
    final String englishTerm;
    final String standaloneQuery;

    AiIntent(int route, int depth, String englishTerm, String standaloneQuery) {
        this.route = route;
        this.depth = depth;
        this.englishTerm = englishTerm;
        this.standaloneQuery = standaloneQuery;
    }

    static AiIntent fallback(String userText) {
        return new AiIntent(ROUTE_SEARCH, DEPTH_STANDARD, null, userText);
    }

    boolean isChat() {
        return route == ROUTE_CHAT;
    }

    String effectiveQuery(String userText) {
        if (standaloneQuery != null && !standaloneQuery.trim().isEmpty()) return standaloneQuery.trim();
        return userText;
    }

    String effectiveTerm() {
        return englishTerm != null && !englishTerm.trim().isEmpty() ? englishTerm.trim() : null;
    }

    static AiIntent parse(String raw, String userText) {
        if (raw == null || raw.trim().isEmpty()) return fallback(userText);

        String route = null;
        String depth = null;
        String term = null;
        String query = null;
        String[] lines = raw.replace("\r", "").split("\n");
        boolean labelled = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            int colon = t.indexOf(':');
            if (colon <= 0) continue;
            String key = t.substring(0, colon).trim().toUpperCase(Locale.ROOT);
            String value = t.substring(colon + 1).trim();
            switch (key) {
                case "ROUTE":
                    route = value;
                    labelled = true;
                    break;
                case "DEPTH":
                    depth = value;
                    labelled = true;
                    break;
                case "TERM":
                    term = cleanValue(value);
                    labelled = true;
                    break;
                case "QUERY":
                    query = cleanValue(value);
                    labelled = true;
                    break;
                default:
                    break;
            }
        }

        if (!labelled) return parseLegacy(raw, userText);

        int routeCode = ROUTE_SEARCH;
        if (route != null) {
            String r = route.toUpperCase(Locale.ROOT);
            if (r.contains("CHAT") && !r.contains("SEARCH")) routeCode = ROUTE_CHAT;
        }
        int depthCode = DEPTH_STANDARD;
        if (depth != null) {
            String d = depth.toUpperCase(Locale.ROOT);
            if (d.contains("QUICK")) depthCode = DEPTH_QUICK;
            else if (d.contains("DEEP")) depthCode = DEPTH_DEEP;
        }
        if (query != null && query.length() > 600) query = query.substring(0, 600);
        return new AiIntent(routeCode, depthCode, term, query);
    }

    private static AiIntent parseLegacy(String raw, String userText) {
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("CHAT") && !upper.contains("SEARCH")) {
            return new AiIntent(ROUTE_CHAT, DEPTH_QUICK, null, userText);
        }
        String term = null;
        String[] lines = raw.replace("\r", "").split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].trim().toUpperCase(Locale.ROOT).startsWith("SEARCH")) {
                term = cleanValue(lines[i + 1]);
                break;
            }
        }
        return new AiIntent(ROUTE_SEARCH, DEPTH_STANDARD, term, userText);
    }

    private static String cleanValue(String v) {
        if (v == null) return null;
        String t = v.trim();
        while (t.length() > 1 && (t.startsWith("\"") || t.startsWith("'") || t.startsWith("`"))) t = t.substring(1);
        while (t.length() > 1 && (t.endsWith("\"") || t.endsWith("'") || t.endsWith("`"))) t = t.substring(0, t.length() - 1);
        t = t.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("none") || t.equalsIgnoreCase("n/a") || t.equals("-")) return null;
        return t;
    }
}
