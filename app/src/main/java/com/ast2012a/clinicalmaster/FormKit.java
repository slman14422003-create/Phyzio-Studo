package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatEditText;

import java.util.ArrayList;
import java.util.List;

final class FormKit {

    private final Context ctx;
    private final LinearLayout root;
    private LinearLayout card;
    private boolean firstInCard;
    private boolean firstSection = true;

    FormKit(Context ctx, LinearLayout root) {
        this.ctx = ctx;
        this.root = root;
    }

    private int dp(float v) {
        return Ui.dp(ctx, v);
    }

    void banner(int scene, int heightDp) {
        IllustrationView v = new IllustrationView(ctx);
        v.setScene(scene);
        root.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
    }

    void intro(int textRes) {
        TextView t = new TextView(ctx, null, 0, R.style.Phizyo_FormSection);
        t.setText(textRes);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        t.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), 0, dp(6), dp(10));
        root.addView(t, lp);
    }

    void section(int titleRes) {
        TextView t = new TextView(ctx, null, 0, R.style.Phizyo_FormSection);
        t.setText(titleRes);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), firstSection ? dp(4) : dp(20), dp(6), dp(8));
        firstSection = false;
        root.addView(t, lp);

        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_settings_group);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        firstInCard = true;
    }

    private void label(int res, boolean required) {
        TextView t = new TextView(ctx, null, 0, R.style.Phizyo_FormLabel);
        t.setText(required ? ctx.getString(res) + " *" : ctx.getString(res));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = firstInCard ? 0 : dp(14);
        lp.bottomMargin = dp(6);
        firstInCard = false;
        card.addView(t, lp);
    }

    EditText text(int labelRes, int hintRes, boolean required, int minLines) {
        label(labelRes, required);
        AppCompatEditText e = new AppCompatEditText(ctx);
        e.setBackgroundResource(R.drawable.bg_input_field);
        e.setMinHeight(dp(52));
        e.setPadding(dp(16), dp(13), dp(16), dp(13));
        e.setTextColor(ctx.getColor(R.color.text_primary));
        e.setHintTextColor(ctx.getColor(R.color.text_tertiary));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        e.setTextDirection(View.TEXT_DIRECTION_RTL);
        e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        if (hintRes != 0) e.setHint(hintRes);
        if (minLines > 1) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(minLines);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        card.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    EditText textWithQuick(int labelRes, int hintRes, boolean required, int minLines, int quickArray) {
        final EditText e = text(labelRes, hintRes, required, minLines);
        FlowLayout flow = newFlow();
        flow.setPadding(0, dp(10), 0, 0);
        for (final String s : ctx.getResources().getStringArray(quickArray)) {
            final TextView chip = chip(s);
            chip.setOnClickListener(v -> insertPhrase(e, s, chip));
            flow.addView(chip);
        }
        card.addView(flow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private void insertPhrase(EditText e, String phrase, TextView chip) {
        String cur = e.getText() == null ? "" : e.getText().toString();
        if (cur.contains(phrase)) {
            bounce(chip);
            return;
        }
        StringBuilder sb = new StringBuilder(cur.trim());
        if (sb.length() > 0) sb.append('\n');
        sb.append(phrase);
        e.setText(sb.toString());
        e.setSelection(e.getText().length());
        paint(chip, true);
        bounce(chip);
        Ui.tick(chip);
    }

    private FlowLayout newFlow() {
        FlowLayout f = new FlowLayout(ctx);
        f.setGaps(8, 8);
        return f;
    }

    private TextView chip(String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        t.setGravity(Gravity.CENTER);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setClickable(true);
        t.setFocusable(true);
        t.setTag(Boolean.FALSE);
        paint(t, false);
        return t;
    }

    private void paint(TextView t, boolean selected) {
        t.setTag(selected ? Boolean.TRUE : Boolean.FALSE);
        t.setBackgroundResource(selected ? R.drawable.bg_glass_chip_selected : R.drawable.bg_glass_chip);
        t.setTextColor(ctx.getColor(selected ? R.color.white : R.color.text_primary));
        t.setPadding(dp(14), dp(9), dp(14), dp(9));
    }

    private boolean isOn(TextView t) {
        return Boolean.TRUE.equals(t.getTag());
    }

    private void bounce(View v) {
        v.animate().cancel();
        v.setScaleX(0.86f);
        v.setScaleY(0.86f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new OvershootInterpolator(3f)).start();
    }

    final class Choice {
        private final boolean multi;
        private final FlowLayout flow;
        private final List<TextView> chips = new ArrayList<>();

        Choice(boolean multi, FlowLayout flow) {
            this.multi = multi;
            this.flow = flow;
        }

        private TextView add(String text) {
            final TextView c = chip(text);
            c.setOnClickListener(v -> toggle(c));
            chips.add(c);
            flow.addView(c);
            return c;
        }

        private void toggle(TextView c) {
            boolean was = isOn(c);
            if (!multi) {
                for (TextView o : chips) paint(o, false);
                paint(c, !was);
            } else {
                paint(c, !was);
            }
            bounce(c);
            Ui.tick(c);
        }

        String get() {
            for (TextView c : chips) if (isOn(c)) return c.getText().toString();
            return "";
        }

        List<String> getList() {
            List<String> out = new ArrayList<>();
            for (TextView c : chips) if (isOn(c)) out.add(c.getText().toString());
            return out;
        }

        void set(String value) {
            List<String> l = new ArrayList<>();
            if (value != null && !value.trim().isEmpty()) l.add(value.trim());
            setList(l);
        }

        void setList(List<String> values) {
            if (values == null) return;
            for (String v : values) {
                if (v == null || v.trim().isEmpty()) continue;
                String val = v.trim();
                TextView match = null;
                for (TextView c : chips) {
                    if (c.getText().toString().equals(val)) {
                        match = c;
                        break;
                    }
                }
                if (match == null) match = add(val);
                paint(match, true);
            }
        }
    }

    Choice single(int labelRes, int arrayRes) {
        return choice(labelRes, arrayRes, false);
    }

    Choice multi(int labelRes, int arrayRes) {
        return choice(labelRes, arrayRes, true);
    }

    private Choice choice(int labelRes, int arrayRes, boolean multi) {
        label(labelRes, false);
        FlowLayout flow = newFlow();
        card.addView(flow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Choice ch = new Choice(multi, flow);
        for (String s : ctx.getResources().getStringArray(arrayRes)) ch.add(s);
        return ch;
    }

    final class Stepper {
        private final int max;
        private int value;
        private final TextView valueView;

        Stepper(int max, TextView valueView) {
            this.max = max;
            this.valueView = valueView;
            render();
        }

        int get() {
            return value;
        }

        void set(int v) {
            value = Math.max(0, Math.min(max, v));
            render();
        }

        private void render() {
            valueView.setText(value == 0 ? "\u2014" : String.valueOf(value));
        }

        private void step(int d) {
            int next = Math.max(0, Math.min(max, value + d));
            if (next == value) return;
            value = next;
            render();
            bounce(valueView);
            Ui.tick(valueView);
        }
    }

    Stepper stepper(int labelRes, int unitRes, int max) {
        label(labelRes, false);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        TextView minus = roundButton("\u2212");
        TextView plus = roundButton("+");
        TextView val = new TextView(ctx);
        val.setGravity(Gravity.CENTER);
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        val.setTypeface(val.getTypeface(), android.graphics.Typeface.BOLD);
        val.setTextColor(ctx.getColor(R.color.text_primary));
        val.setMinWidth(dp(64));

        TextView unit = new TextView(ctx);
        unit.setText(unitRes);
        unit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        unit.setTextColor(ctx.getColor(R.color.text_secondary));
        unit.setTextDirection(View.TEXT_DIRECTION_RTL);

        row.addView(minus, new LinearLayout.LayoutParams(dp(46), dp(46)));
        row.addView(val, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(plus, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.setMarginStart(dp(12));
        row.addView(unit, ulp);

        final Stepper s = new Stepper(max, val);
        minus.setOnClickListener(v -> s.step(-1));
        plus.setOnClickListener(v -> s.step(1));

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.gravity = Gravity.START;
        card.addView(row, rlp);
        return s;
    }

    private TextView roundButton(String symbol) {
        TextView b = new TextView(ctx);
        b.setText(symbol);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        b.setTextColor(ctx.getColor(R.color.primary_cyan_dark));
        b.setBackgroundResource(R.drawable.bg_glass_chip);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    static String textOf(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
}
