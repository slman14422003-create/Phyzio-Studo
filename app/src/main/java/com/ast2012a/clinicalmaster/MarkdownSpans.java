package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import androidx.core.content.ContextCompat;

final class MarkdownSpans {

    private MarkdownSpans() {}

    static CharSequence render(Context ctx, String raw) {
        MarkdownLite.Doc doc = MarkdownLite.parse(raw);
        SpannableStringBuilder sb = new SpannableStringBuilder(doc.text);
        int accent = ContextCompat.getColor(ctx, R.color.primary_cyan_dark);
        int secondary = ContextCompat.getColor(ctx, R.color.text_secondary);
        int codeBg = ContextCompat.getColor(ctx, R.color.m3_surface_container);
        int len = sb.length();

        for (MarkdownLite.Span s : doc.spans) {
            int start = s.start;
            int end = Math.min(s.end, len);
            if (end <= start) continue;
            int paraEnd = Math.min(end + 1, len);
            switch (s.kind) {
                case MarkdownLite.BOLD:
                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case MarkdownLite.ITALIC:
                    sb.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case MarkdownLite.HEADING: {
                    float size = s.level <= 1 ? 1.22f : (s.level == 2 ? 1.12f : 1.05f);
                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new RelativeSizeSpan(size), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new ForegroundColorSpan(accent), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                case MarkdownLite.LABEL:
                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new ForegroundColorSpan(accent), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case MarkdownLite.BULLET:
                case MarkdownLite.NUMBERED: {
                    int first = Ui.dp(ctx, 4 + 14 * s.level);
                    int rest = first + Ui.dp(ctx, s.kind == MarkdownLite.BULLET ? 16 : 22);
                    sb.setSpan(new LeadingMarginSpan.Standard(first, rest), start, paraEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                case MarkdownLite.QUOTE:
                    sb.setSpan(new LeadingMarginSpan.Standard(Ui.dp(ctx, 12)), start, paraEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new ForegroundColorSpan(secondary), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case MarkdownLite.CODE:
                    sb.setSpan(new TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new BackgroundColorSpan(codeBg), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                default:
                    break;
            }
        }
        return sb;
    }
}
