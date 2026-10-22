package com.ast2012a.clinicalmaster;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownLite {

    static final int BOLD = 1;
    static final int ITALIC = 2;
    static final int HEADING = 3;
    static final int BULLET = 4;
    static final int NUMBERED = 5;
    static final int QUOTE = 6;
    static final int CODE = 7;
    static final int LABEL = 8;

    static final class Span {
        final int kind;
        final int start;
        final int end;
        final int level;

        Span(int kind, int start, int end, int level) {
            this.kind = kind;
            this.start = start;
            this.end = end;
            this.level = level;
        }
    }

    static final class Doc {
        final String text;
        final List<Span> spans;

        Doc(String text, List<Span> spans) {
            this.text = text;
            this.spans = spans;
        }
    }

    private static final Pattern HEADING_LINE = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern BULLET_LINE = Pattern.compile("^(\\s*)([-*\\u2022\\u25CF\\u25AA+])\\s+(.*)$");
    private static final Pattern NUMBER_LINE = Pattern.compile("^(\\s*)(\\d{1,3})[.)\\u066B]\\s+(.*)$");
    private static final Pattern QUOTE_LINE = Pattern.compile("^\\s*>\\s?(.*)$");
    private static final Pattern RULE_LINE = Pattern.compile("^\\s*([-*_=])(\\s*\\1){2,}\\s*$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern TABLE_SEP = Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$");
    private static final Pattern FENCE = Pattern.compile("^\\s*```.*$");
    private static final Pattern HTML_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern HTML_TAG = Pattern.compile("(?i)</?(b|strong|i|em|u|p|div|span|ul|ol|li|h[1-6])(\\s[^>]*)?>");

    private MarkdownLite() {}

    static String toPlain(String raw) {
        return parse(raw).text;
    }

    static Doc parse(String raw) {
        if (raw == null || raw.isEmpty()) return new Doc("", new ArrayList<Span>());
        String src = HTML_BREAK.matcher(raw.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("\n");
        src = HTML_TAG.matcher(src).replaceAll("");
        String[] lines = src.split("\n", -1);

        StringBuilder out = new StringBuilder(src.length());
        List<Span> spans = new ArrayList<>();
        boolean lastBlank = true;
        boolean inFence = false;
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];

            if (FENCE.matcher(line).matches()) {
                inFence = !inFence;
                i++;
                continue;
            }
            if (inFence) {
                int s = out.length();
                out.append(line);
                spans.add(new Span(CODE, s, out.length(), 0));
                out.append('\n');
                lastBlank = false;
                i++;
                continue;
            }

            if (line.trim().isEmpty()) {
                if (!lastBlank) out.append('\n');
                lastBlank = true;
                i++;
                continue;
            }

            if (RULE_LINE.matcher(line).matches()) {
                i++;
                continue;
            }

            if (TABLE_ROW.matcher(line).matches() && i + 1 < lines.length
                    && TABLE_SEP.matcher(lines[i + 1]).matches()) {
                List<String> header = splitRow(line);
                int j = i + 2;
                while (j < lines.length && TABLE_ROW.matcher(lines[j]).matches()) {
                    List<String> cells = splitRow(lines[j]);
                    emitTableRow(out, spans, header, cells);
                    lastBlank = false;
                    j++;
                }
                i = j;
                continue;
            }

            Matcher m = HEADING_LINE.matcher(line);
            if (m.matches()) {
                int level = Math.min(3, m.group(1).length());
                int s = out.length();
                appendInline(out, spans, m.group(2));
                spans.add(new Span(HEADING, s, out.length(), level));
                out.append('\n');
                lastBlank = false;
                i++;
                continue;
            }

            m = BULLET_LINE.matcher(line);
            if (m.matches()) {
                int level = Math.min(3, m.group(1).replace("\t", "    ").length() / 2);
                int s = out.length();
                out.append("\u2022 ");
                appendInline(out, spans, m.group(3));
                spans.add(new Span(BULLET, s, out.length(), level));
                out.append('\n');
                lastBlank = false;
                i++;
                continue;
            }

            m = NUMBER_LINE.matcher(line);
            if (m.matches()) {
                int level = Math.min(3, m.group(1).replace("\t", "    ").length() / 2);
                int s = out.length();
                out.append(m.group(2)).append(". ");
                appendInline(out, spans, m.group(3));
                spans.add(new Span(NUMBERED, s, out.length(), level));
                out.append('\n');
                lastBlank = false;
                i++;
                continue;
            }

            m = QUOTE_LINE.matcher(line);
            if (m.matches()) {
                int s = out.length();
                appendInline(out, spans, m.group(1));
                spans.add(new Span(QUOTE, s, out.length(), 0));
                out.append('\n');
                lastBlank = false;
                i++;
                continue;
            }

            String trimmed = line.trim();
            boolean prevBlank = lastBlank;
            boolean nextBlank = i + 1 >= lines.length || lines[i + 1].trim().isEmpty();
            if (prevBlank && looksLikeLabelLine(trimmed) && !nextBlank) {
                int s = out.length();
                appendInline(out, spans, trimmed);
                spans.add(new Span(LABEL, s, out.length(), 0));
                out.append('\n');
                lastBlank = false;
                i++;
                continue;
            }

            appendInline(out, spans, trimmed);
            out.append('\n');
            lastBlank = false;
            i++;
        }

        int end = out.length();
        while (end > 0 && Character.isWhitespace(out.charAt(end - 1))) end--;
        int start = 0;
        while (start < end && Character.isWhitespace(out.charAt(start))) start++;
        return trimDoc(out.toString(), spans, start, end);
    }

    private static Doc trimDoc(String text, List<Span> spans, int start, int end) {
        String trimmed = text.substring(start, end);
        List<Span> shifted = new ArrayList<>(spans.size());
        for (Span s : spans) {
            int a = Math.max(s.start - start, 0);
            int b = Math.min(s.end - start, trimmed.length());
            if (b > a) shifted.add(new Span(s.kind, a, b, s.level));
        }
        return new Doc(trimmed, shifted);
    }

    private static boolean looksLikeLabelLine(String t) {
        if (t.length() < 2 || t.length() > 70) return false;
        char last = t.charAt(t.length() - 1);
        if (last != ':' && last != '\uFF1A') return false;
        return t.indexOf(':') == t.length() - 1 || t.indexOf('\uFF1A') == t.length() - 1;
    }

    private static List<String> splitRow(String row) {
        String t = row.trim();
        if (t.startsWith("|")) t = t.substring(1);
        if (t.endsWith("|")) t = t.substring(0, t.length() - 1);
        String[] parts = t.split("\\|", -1);
        List<String> cells = new ArrayList<>(parts.length);
        for (String p : parts) cells.add(p.trim());
        return cells;
    }

    private static void emitTableRow(StringBuilder out, List<Span> spans, List<String> header, List<String> cells) {
        if (cells.isEmpty()) return;
        int s = out.length();
        out.append("\u2022 ");
        int boldStart = out.length();
        appendInline(out, spans, cells.get(0));
        if (out.length() > boldStart) spans.add(new Span(BOLD, boldStart, out.length(), 0));
        boolean first = true;
        for (int c = 1; c < cells.size(); c++) {
            String cell = cells.get(c);
            if (cell.isEmpty()) continue;
            out.append(first ? ": " : "  |  ");
            first = false;
            String h = c < header.size() ? stripInlineMarks(header.get(c)) : "";
            if (!h.isEmpty() && cells.size() > 2) {
                out.append(h).append(" \u2014 ");
            }
            appendInline(out, spans, cell);
        }
        spans.add(new Span(BULLET, s, out.length(), 0));
        out.append('\n');
    }

    private static String stripInlineMarks(String s) {
        return s.replace("**", "").replace("__", "").replace("`", "").trim();
    }

    private static void appendInline(StringBuilder out, List<Span> spans, String text) {
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);

            if (c == '`') {
                int j = text.indexOf('`', i + 1);
                if (j > i + 1) {
                    int s = out.length();
                    out.append(text, i + 1, j);
                    spans.add(new Span(CODE, s, out.length(), 0));
                    i = j + 1;
                    continue;
                }
                i++;
                continue;
            }

            if ((c == '*' || c == '_') && i + 1 < n && text.charAt(i + 1) == c) {
                int j = text.indexOf("" + c + c, i + 2);
                if (j > i + 2 && !Character.isWhitespace(text.charAt(i + 2))) {
                    int s = out.length();
                    appendInline(out, spans, text.substring(i + 2, j));
                    if (out.length() > s) spans.add(new Span(BOLD, s, out.length(), 0));
                    i = j + 2;
                    continue;
                }
                i += 2;
                continue;
            }

            if (c == '*' && i + 1 < n && !Character.isWhitespace(text.charAt(i + 1))) {
                int j = text.indexOf('*', i + 1);
                if (j > i + 1 && !Character.isWhitespace(text.charAt(j - 1))
                        && !(i > 0 && Character.isLetterOrDigit(text.charAt(i - 1)))) {
                    int s = out.length();
                    appendInline(out, spans, text.substring(i + 1, j));
                    if (out.length() > s) spans.add(new Span(ITALIC, s, out.length(), 0));
                    i = j + 1;
                    continue;
                }
                if (i > 0 && Character.isDigit(text.charAt(i - 1))) {
                    out.append('\u00D7');
                }
                i++;
                continue;
            }

            if (c == '*') {
                i++;
                continue;
            }

            out.append(c);
            i++;
        }
    }
}
