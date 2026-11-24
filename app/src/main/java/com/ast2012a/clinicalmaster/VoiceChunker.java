package com.ast2012a.clinicalmaster;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Splits text into sentence-based pieces of bounded length and keeps the start and end offset of every piece. */
final class VoiceChunker {
    final int[] starts;
    final int[] ends;

    private VoiceChunker(int[] s, int[] e) {
        starts = s;
        ends = e;
    }

    int count() {
        return starts.length;
    }

    static boolean arabicDominant(String text) {
        int ar = 0;
        int lat = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0x0600 && ch <= 0x06FF) ar++;
            else if (Character.isLetter(ch)) lat++;
        }
        return ar >= lat;
    }

    static VoiceChunker split(String text, int max) {
        List<int[]> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return new VoiceChunker(new int[0], new int[0]);
        Locale loc = arabicDominant(text) ? Locale.forLanguageTag("ar") : Locale.ENGLISH;
        BreakIterator bi = BreakIterator.getSentenceInstance(loc);
        bi.setText(text);
        int curS = -1;
        int curE = -1;
        int a = bi.first();
        for (int b = bi.next(); b != BreakIterator.DONE; a = b, b = bi.next()) {
            int s = a;
            int e = b;
            while (s < e && Character.isWhitespace(text.charAt(s))) s++;
            while (e > s && Character.isWhitespace(text.charAt(e - 1))) e--;
            if (e <= s) continue;
            if (e - s > max) {
                if (curS >= 0) {
                    out.add(new int[]{curS, curE});
                    curS = -1;
                }
                int p = s;
                while (p < e) {
                    int q = Math.min(e, p + max);
                    if (q < e) {
                        int cut = q;
                        while (cut > p && !Character.isWhitespace(text.charAt(cut - 1))) cut--;
                        if (cut > p) q = cut;
                    }
                    int ce = q;
                    while (ce > p && Character.isWhitespace(text.charAt(ce - 1))) ce--;
                    if (ce > p) out.add(new int[]{p, ce});
                    p = q;
                    while (p < e && Character.isWhitespace(text.charAt(p))) p++;
                }
                continue;
            }
            if (curS < 0) {
                curS = s;
                curE = e;
            } else if (e - curS <= max) {
                curE = e;
            } else {
                out.add(new int[]{curS, curE});
                curS = s;
                curE = e;
            }
        }
        if (curS >= 0) out.add(new int[]{curS, curE});
        int[] st = new int[out.size()];
        int[] en = new int[out.size()];
        for (int i = 0; i < st.length; i++) {
            st[i] = out.get(i)[0];
            en[i] = out.get(i)[1];
        }
        return new VoiceChunker(st, en);
    }

    /** Index of the piece that starts a new paragraph after from (direction 1) or before it (direction -1). */
    int paragraphStep(String text, int from, int direction) {
        int n = starts.length;
        if (n == 0) return 0;
        if (direction > 0) {
            for (int i = from + 1; i < n; i++) if (startsParagraph(text, i)) return i;
            return n;
        }
        int i = from;
        if (i < n && startsParagraph(text, i) && i > 0) i--;
        for (; i > 0; i--) if (startsParagraph(text, i)) return i;
        return 0;
    }

    boolean startsParagraph(String text, int i) {
        if (i <= 0) return true;
        int gap = starts[i] - ends[i - 1];
        if (gap <= 0) return false;
        int nl = 0;
        for (int k = ends[i - 1]; k < starts[i]; k++) if (text.charAt(k) == 10) nl++;
        return nl >= 2;
    }
}
