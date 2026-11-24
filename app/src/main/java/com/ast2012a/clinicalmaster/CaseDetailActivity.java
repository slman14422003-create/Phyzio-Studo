package com.ast2012a.clinicalmaster;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

/**
 * شاشة تفاصيل الحالة (البروتوكول) — إعادة تصميم كاملة.
 *
 * الترتيب: عنوان وشارات النوع ← إعدادات الجهاز (النمط/التردد/القناة/المدة)
 * ← وضع الأقطاب كبطاقات قنوات بعلامات (+) و(−) ← الشرح السريري ← الأعراض
 * والخطة والنصيحة ← ملاحظات السلامة ← الإجراءات. النصوص الحرة في قاعدة
 * البيانات تُحلَّل عبر ProtocolParser، وأي نص لا يُفهم بثقة يُعرض كما هو.
 */
public class CaseDetailActivity extends AppCompatActivity {

    private CaseItem currentCase;
    private LinearLayout container;
    private MaterialToolbar toolbar;
    private LayoutInflater inflater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        inflater = LayoutInflater.from(this);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_detail);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        container = findViewById(R.id.detail_container);

        loadCaseFromIntent();
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_favorite) {
            if (currentCase == null) return true;
            boolean nowFavorite = FavoritesManager.toggleFavorite(this, currentCase.title);
            refreshFavoriteIcon(nowFavorite);
            return true;
        }
        if (id == R.id.action_share) {
            shareCase();
            return true;
        }
        return false;
    }

    private void refreshFavoriteIcon(boolean isFavorite) {
        MenuItem favItem = toolbar.getMenu().findItem(R.id.action_favorite);
        if (favItem == null) return;
        favItem.setIcon(isFavorite ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        favItem.setTitle(isFavorite ? "إزالة من المفضلة" : "إضافة للمفضلة");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // إعادة التحميل عند الرجوع من شاشة التعديل حتى تنعكس التغييرات فورًا
        loadCaseFromIntent();
    }

    private void loadCaseFromIntent() {
        String caseId = getIntent().getStringExtra("case_id");
        String caseTitle = getIntent().getStringExtra("case_title");

        CaseItem found = null;
        for (CaseItem c : DataManager.loadCustomCases(this)) {
            if (caseId != null ? caseId.equals(c.id) : (caseTitle != null && caseTitle.equals(c.title))) {
                found = c;
                break;
            }
        }

        if (found == null) {
            finish();
            return;
        }
        currentCase = found;
        refreshFavoriteIcon(FavoritesManager.isFavorite(this, currentCase.title));
        render();
    }

    // =====================================================================
    // البناء
    // =====================================================================

    private void render() {
        container.removeAllViews();

        addHero();
        addSpecCard();

        if (ProtocolParser.isElectrodeProtocol(currentCase.mode)) {
            addElectrodes();
        } else {
            addPlainDetails();
        }

        addSection(R.string.lbl_explanation, currentCase.explanation, false, false);
        addSection(R.string.lbl_symptoms, currentCase.symptoms, false, false);
        addSection(R.string.lbl_assessment, currentCase.assessment, false, false);
        addSection(R.string.lbl_goals, currentCase.goals, false, false);
        addSection(R.string.lbl_dosage, currentCase.dosage, false, false);
        addSection(R.string.lbl_sessions_plan, currentCase.sessionsPlan, false, false);
        addSection(R.string.lbl_home, currentCase.homeExercises, false, false);
        addSection(R.string.lbl_progression, currentCase.progression, false, false);
        addSection(R.string.lbl_precautions, currentCase.precautions, true, false);
        addSection(R.string.lbl_tip, currentCase.tip, false, true);

        addSafety();
        addActions();
    }

    private void addSection(int titleRes, String text, boolean warning, boolean tip) {
        if (!notEmpty(text)) return;
        String[] lines = text.trim().split("\\r?\\n");
        int count = 0;
        for (String l : lines) if (!l.trim().isEmpty()) count++;
        String title = getString(titleRes);

        if (count <= 1 || tip) {
            View card = inflater.inflate(R.layout.item_text_card, container, false);
            TextView label = card.findViewById(R.id.text_card_label);
            TextView body = card.findViewById(R.id.text_card_body);
            label.setText(title);
            body.setText(BidiText.fix(text.trim()));
            if (warning) {
                card.setBackgroundResource(R.drawable.bg_glass_card_warning);
                label.setTextColor(getColor(R.color.accent_red));
            }
            if (tip) {
                card.setBackgroundResource(R.drawable.bg_tip_callout);
                label.setTextColor(getColor(R.color.m3_on_tertiary_container));
                body.setTextColor(getColor(R.color.m3_on_tertiary_container));
            }
            container.addView(card);
            return;
        }

        View card = inflater.inflate(R.layout.item_list_card, container, false);
        if (warning) card.setBackgroundResource(R.drawable.bg_glass_card_warning);
        TextView titleView = card.findViewById(R.id.list_title);
        titleView.setText(title);
        if (warning) titleView.setTextColor(getColor(R.color.accent_red));
        LinearLayout list = card.findViewById(R.id.list_container);
        for (String l : lines) {
            String clean = l.trim().replaceFirst("^[\\-\\u2022*]\\s+", "");
            if (clean.isEmpty()) continue;
            addBullet(list, clean);
        }
        container.addView(card);
    }

    private int heroScene() {
        String[] keys = getResources().getStringArray(R.array.key_spine);
        StringBuilder sb = new StringBuilder();
        sb.append(currentCase.category).append(' ').append(CaseItem.joinList(currentCase.regions));
        String blob = sb.toString();
        for (String k : keys) if (blob.contains(k)) return IllustrationView.SPINE;
        if (CaseItem.joinList(currentCase.modalities).contains(getString(R.string.key_exercise))) {
            return IllustrationView.EXERCISE;
        }
        return IllustrationView.PULSE;
    }

    private boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void addSectionTitle(String title) {
        TextView t = (TextView) inflater.inflate(R.layout.item_section_title, container, false);
        t.setText(title);
        container.addView(t);
    }

    // ---------------------------------------------------------------- الرأس

    private void addHero() {
        View hero = inflater.inflate(R.layout.item_detail_hero, container, false);
        LinearLayout badges = hero.findViewById(R.id.hero_badges);
        TextView titleView = hero.findViewById(R.id.hero_title);
        TextView englishView = hero.findViewById(R.id.hero_english);

        if (hero instanceof LinearLayout) {
            IllustrationView ill = new IllustrationView(this);
            ill.setScene(heroScene());
            ((LinearLayout) hero).addView(ill, 0, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(124)));
        }

        ProtocolParser.TitleParts parts = ProtocolParser.splitTitle(currentCase.title);
        titleView.setText(BidiText.fix(parts.main));
        if (!parts.english.isEmpty()) {
            englishView.setText(parts.english);
            englishView.setVisibility(View.VISIBLE);
        }

        if (notEmpty(currentCase.category)) {
            TextView b = addBadge(badges, currentCase.category, "EMS");
            b.setTextDirection(View.TEXT_DIRECTION_RTL);
        }
        if (notEmpty(currentCase.stage)) {
            TextView b = addBadge(badges, currentCase.stage, "");
            b.setTextDirection(View.TEXT_DIRECTION_RTL);
        }
        if (notEmpty(currentCase.severity)) {
            TextView b = addBadge(badges, currentCase.severity, "");
            b.setTextDirection(View.TEXT_DIRECTION_RTL);
            b.setBackgroundResource(R.drawable.bg_source_chip);
            b.setTextColor(getColor(R.color.m3_on_primary_container));
        }
        if (ProtocolParser.hasTens(currentCase.mode)) {
            addBadge(badges, "TENS", "TENS");
        }
        if (ProtocolParser.hasEms(currentCase.mode)) {
            addBadge(badges, "EMS", "EMS");
        }
        if (badges.getChildCount() == 0) badges.setVisibility(View.GONE);

        container.addView(hero);
    }

    /** يضيف شارة صغيرة؛ type = "TENS" أو "EMS" يحدد اللون، وأي قيمة أخرى تعني لون TENS. */
    private TextView addBadge(LinearLayout parent, String text, String type) {
        TextView badge = (TextView) inflater.inflate(R.layout.item_badge, parent, false);
        badge.setText(text);
        applyBadgeColors(badge, type);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) badge.getLayoutParams();
        lp.setMarginEnd(dp(6));
        parent.addView(badge);
        return badge;
    }

    private void applyBadgeColors(TextView badge, String type) {
        if ("EMS".equals(type)) {
            badge.setBackgroundResource(R.drawable.bg_badge_ems);
            badge.setTextColor(getColor(R.color.m3_on_primary_container));
        } else {
            badge.setBackgroundResource(R.drawable.bg_badge_tens);
            badge.setTextColor(getColor(R.color.m3_on_secondary_container));
        }
    }

    // ------------------------------------------------------- إعدادات الجهاز

    private void addSpecCard() {
        View card = inflater.inflate(R.layout.item_spec_card, container, false);
        LinearLayout rows = card.findViewById(R.id.spec_rows);
        boolean first = true;

        String regions = CaseItem.joinList(currentCase.regions);
        if (notEmpty(regions)) {
            addPlainValue(addSpecRow(rows, getString(R.string.lbl_region), first), regions);
            first = false;
        }
        String modalities = CaseItem.joinList(currentCase.modalities);
        if (notEmpty(modalities)) {
            addPlainValue(addSpecRow(rows, getString(R.string.lbl_modalities), first), modalities);
            first = false;
        }
        if (currentCase.sessionsPerWeek > 0 || currentCase.totalSessions > 0) {
            String perWeek = currentCase.sessionsPerWeek > 0
                    ? getString(R.string.fmt_per_week, currentCase.sessionsPerWeek) : "";
            String total = currentCase.totalSessions > 0
                    ? getString(R.string.fmt_total, currentCase.totalSessions) : "";
            String value = perWeek.isEmpty() ? total : (total.isEmpty() ? perWeek
                    : getString(R.string.fmt_join_dot, perWeek, total));
            addPlainValue(addSpecRow(rows, getString(R.string.sec_dosage), first), value);
            first = false;
        }
        if (notEmpty(currentCase.duration)) {
            addPlainValue(addSpecRow(rows, getString(R.string.lbl_session_duration), first), currentCase.duration);
            first = false;
        }

        if (ProtocolParser.isElectrodeProtocol(currentCase.mode)) {
            if (notEmpty(currentCase.mode)) {
                LinearLayout values = addSpecRow(rows, "\u0627\u0644\u0646\u0645\u0637", first);
                first = false;
                ProtocolParser.Modes modes = ProtocolParser.parseModes(currentCase.mode);
                if (modes.chips.isEmpty()) {
                    addPlainValue(values, currentCase.mode);
                } else {
                    for (ProtocolParser.ModeChip chip : modes.chips) addModeLine(values, chip);
                }
            }
            if (notEmpty(currentCase.freq)) {
                LinearLayout values = addSpecRow(rows, "\u0627\u0644\u062a\u0631\u062f\u062f", first);
                first = false;
                for (ProtocolParser.FreqLine line : ProtocolParser.parseFrequency(currentCase.freq)) {
                    addFreqLine(values, line);
                }
            }
            if (notEmpty(currentCase.channel)) {
                LinearLayout values = addSpecRow(rows, "\u0627\u0644\u0642\u0646\u0627\u0629", first);
                first = false;
                addPlainValue(values, currentCase.channel);
            }
        }

        if (rows.getChildCount() > 0) container.addView(card);
    }

    private LinearLayout addSpecRow(LinearLayout rows, String label, boolean first) {
        View row = inflater.inflate(R.layout.item_spec_row, rows, false);
        ((TextView) row.findViewById(R.id.spec_label)).setText(label);
        if (first) row.findViewById(R.id.spec_divider).setVisibility(View.GONE);
        rows.addView(row);
        return row.findViewById(R.id.spec_values);
    }

    private void addPlainValue(LinearLayout values, String text) {
        TextView v = (TextView) inflater.inflate(R.layout.item_plain_value, values, false);
        v.setText(BidiText.fix(text));
        v.setTextSize(15f);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        values.addView(v);
    }

    private void addModeLine(LinearLayout values, ProtocolParser.ModeChip chip) {
        View line = inflater.inflate(R.layout.item_mode_line, values, false);
        TextView badge = line.findViewById(R.id.mode_badge);
        TextView text = line.findViewById(R.id.mode_text);
        TextView purpose = line.findViewById(R.id.mode_purpose);

        if (chip.type.isEmpty()) {
            badge.setVisibility(View.GONE);
        } else {
            badge.setText(chip.type);
            applyBadgeColors(badge, chip.type);
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(BidiText.fix("نمط " + chip.number));
        if (!chip.name.isEmpty()) {
            int start = sb.length();
            sb.append("  ").append(BidiText.fix(chip.name));
            sb.setSpan(new ForegroundColorSpan(getColor(R.color.text_secondary)),
                    start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        text.setText(sb);

        if (!chip.purpose.isEmpty()) {
            purpose.setText(BidiText.fix(chip.purpose));
            purpose.setVisibility(View.VISIBLE);
        }
        values.addView(line);
    }

    private void addFreqLine(LinearLayout values, ProtocolParser.FreqLine line) {
        View row = inflater.inflate(R.layout.item_freq_line, values, false);
        TextView label = row.findViewById(R.id.freq_label);
        TextView value = row.findViewById(R.id.freq_value);
        TextView note = row.findViewById(R.id.freq_note);

        if (!line.label.isEmpty()) {
            label.setText(line.label);
            applyBadgeColors(label, line.label);
            label.setVisibility(View.VISIBLE);
        }
        if (line.value.isEmpty()) {
            value.setVisibility(View.GONE);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) note.getLayoutParams();
            lp.setMarginStart(0);
            note.setTextSize(14.5f);
            note.setTextColor(getColor(R.color.text_primary));
        } else {
            value.setText(line.value);
        }
        if (line.note.isEmpty()) {
            note.setVisibility(View.GONE);
        } else {
            note.setText(BidiText.fix(line.note));
        }
        values.addView(row);
    }

    // --------------------------------------------------------------- الأقطاب

    private void addElectrodes() {
        List<ProtocolParser.PoleCard> cards = ProtocolParser.parsePoles(currentCase.poles);
        if (cards.isEmpty()) return;
        addSectionTitle("وضع الأقطاب");

        for (ProtocolParser.PoleCard pc : cards) {
            View card = inflater.inflate(R.layout.item_pole_card, container, false);
            TextView label = card.findViewById(R.id.pole_label);
            LinearLayout rows = card.findViewById(R.id.pole_rows);
            LinearLayout notes = card.findViewById(R.id.pole_notes);

            if (!pc.label.isEmpty()) {
                label.setText(BidiText.fix(pc.label));
                label.setVisibility(View.VISIBLE);
            }

            for (ProtocolParser.Terminal t : pc.terminals) {
                View row = inflater.inflate(R.layout.item_pole_terminal, rows, false);
                LinearLayout badgeBox = row.findViewById(R.id.terminal_badges);
                if (t.positive) addPolarityBadge(badgeBox, true);
                if (t.negative) addPolarityBadge(badgeBox, false);
                ((TextView) row.findViewById(R.id.terminal_text)).setText(BidiText.fix(t.text));
                rows.addView(row);
            }

            for (String n : pc.notes) {
                TextView note = (TextView) inflater.inflate(R.layout.item_plain_value, notes, false);
                note.setText(BidiText.fix(n));
                note.setTextColor(getColor(R.color.text_secondary));
                note.setTextSize(13.5f);
                note.setPadding(0, 0, 0, dp(8));
                notes.addView(note);
            }
            container.addView(card);
        }
    }

    private void addPolarityBadge(LinearLayout box, boolean positive) {
        TextView b = (TextView) inflater.inflate(R.layout.item_polarity_badge, box, false);
        b.setText(positive ? "+" : "\u2212");
        b.setBackgroundResource(positive ? R.drawable.bg_polarity_pos : R.drawable.bg_polarity_neg);
        b.setTextColor(getColor(positive ? R.color.m3_on_error_container : R.color.m3_on_secondary_container));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
        lp.setMarginEnd(dp(4));
        box.addView(b);
    }

    /** للحالات غير المرتبطة بأقطاب (مواصفات، أمان، استكشاف أخطاء): قائمة نقطية بسيطة. */
    private void addPlainDetails() {
        if (currentCase.poles == null || currentCase.poles.isEmpty()) return;
        addSectionTitle("التفاصيل");

        boolean warning = false;
        for (String line : currentCase.poles) {
            String t = line == null ? "" : line.trim();
            if (t.startsWith("\u26D4") || t.startsWith("\u26A0")) warning = true;
        }

        View card = inflater.inflate(R.layout.item_list_card, container, false);
        if (warning) card.setBackgroundResource(R.drawable.bg_glass_card_warning);
        card.findViewById(R.id.list_title).setVisibility(View.GONE);
        LinearLayout list = card.findViewById(R.id.list_container);
        for (String line : currentCase.poles) {
            if (line == null || line.trim().isEmpty()) continue;
            String clean = line.trim().replaceFirst("^\\((\\+|-)\\)\\s*", "");
            addBullet(list, clean);
        }
        container.addView(card);
    }

    private void addBullet(LinearLayout list, String text) {
        View row = inflater.inflate(R.layout.item_bullet_row, list, false);
        ((TextView) row.findViewById(R.id.bullet_text)).setText(BidiText.fix(text));
        list.addView(row);
    }

    // ------------------------------------------------------ نصوص وسلامة

    private void addTextCard(String label, String body, boolean tip) {
        if (!notEmpty(body)) return;
        View card = inflater.inflate(R.layout.item_text_card, container, false);
        TextView labelView = card.findViewById(R.id.text_card_label);
        TextView bodyView = card.findViewById(R.id.text_card_body);

        if (label == null) {
            labelView.setVisibility(View.GONE);
        } else {
            labelView.setText(label);
        }
        bodyView.setText(BidiText.fix(body.trim()));

        if (tip) {
            card.setBackgroundResource(R.drawable.bg_tip_callout);
            labelView.setTextColor(getColor(R.color.m3_on_tertiary_container));
            bodyView.setTextColor(getColor(R.color.m3_on_tertiary_container));
        }
        container.addView(card);
    }

    private void addSafety() {
        List<String> notes = DataManager.getGeneralSafetyNote(currentCase.mode);
        if (notes.isEmpty()) return;

        View card = inflater.inflate(R.layout.item_list_card, container, false);
        card.setBackgroundResource(R.drawable.bg_glass_card_warning);
        TextView title = card.findViewById(R.id.list_title);
        title.setText("ملاحظات السلامة");
        title.setTextColor(getColor(R.color.accent_red));
        LinearLayout list = card.findViewById(R.id.list_container);
        for (String n : notes) addBullet(list, n);
        container.addView(card);
    }

    // ------------------------------------------------------------- الإجراءات

    private void addActions() {
        View actions = inflater.inflate(R.layout.item_detail_actions, container, false);

        actions.findViewById(R.id.btn_log_session).setOnClickListener(v -> logSessionForPatient());

        actions.findViewById(R.id.btn_ask_ai).setOnClickListener(v -> {
            Intent i = new Intent(this, AiAssistantActivity.class);
            i.putExtra("prefill_query", "أخبرني المزيد عن: " + currentCase.title);
            startActivity(i);
            Motion.forward(this);
        });

        final String term = ProtocolParser.searchTerm(currentCase.title);
        if (term.isEmpty()) {
            actions.findViewById(R.id.sources_box).setVisibility(View.GONE);
        } else {
            actions.findViewById(R.id.btn_physio).setOnClickListener(v -> openExternalSearch(
                    "https://www.physio-pedia.com/index.php?search=", term));
            actions.findViewById(R.id.btn_pubmed).setOnClickListener(v -> openExternalSearch(
                    "https://pubmed.ncbi.nlm.nih.gov/?term=", term));
        }

        View customRow = actions.findViewById(R.id.custom_row);
        TextView deleteBtn = actions.findViewById(R.id.btn_delete_case);
        customRow.setVisibility(View.VISIBLE);
        actions.findViewById(R.id.btn_edit_case).setOnClickListener(v -> editCase());
        deleteBtn.setVisibility(View.VISIBLE);
        deleteBtn.setText(R.string.case_delete_action);
        deleteBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_trash, 0, 0, 0);
        deleteBtn.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent_red)));
        deleteBtn.setTextColor(getColor(R.color.accent_red));
        deleteBtn.setOnClickListener(v -> confirmDelete());
        container.addView(actions);
    }

    private void logSessionForPatient() {
        final List<Patient> patients = PatientManager.loadPatients(this);
        if (patients.isEmpty()) {
            Toast.makeText(this, "أضف مريضًا أولًا من شاشة المرضى.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[patients.size()];
        for (int i = 0; i < names.length; i++) {
            String n = patients.get(i).name;
            names[i] = n.isEmpty() ? "(بدون اسم)" : n;
        }
        new ClaudeDialog(this)
                .setTitle("لأي مريض؟")
                .setItems(names, (dialog, which) -> {
                    Patient p = patients.get(which);
                    PatientDialogs.showSession(this, p.id, null, currentCase.title,
                            () -> Toast.makeText(this, "تم تسجيل الجلسة في ملف المريض.", Toast.LENGTH_SHORT).show());
                })
                .show();
    }

    private void shareCase() {
        if (currentCase == null) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, currentCase.title);
        send.putExtra(Intent.EXTRA_TEXT, currentCase.toShareText(this));
        startActivity(Intent.createChooser(send, "مشاركة الحالة"));
    }

    private void openExternalSearch(String baseUrl, String term) {
        try {
            Uri uri = Uri.parse(baseUrl + Uri.encode(term));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "لا يوجد متصفح لفتح الرابط.", Toast.LENGTH_SHORT).show();
        }
    }

    private void editCase() {
        Intent i = new Intent(this, AddEditCaseActivity.class);
        i.putExtra("edit_case_id", currentCase.id);
        startActivity(i);
        Motion.forward(this);
    }

    private void confirmDelete() {
        new ClaudeDialog(this)
                .setTitle("تأكيد الحذف")
                .setMessage("هل تريد حذف \"" + currentCase.title + "\" نهائيًا؟")
                .setPositiveButton("حذف", (dialog, which) -> {
                    DataManager.deleteCustomCase(this, currentCase.id);
                    finish();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    @Override
    public void finish() {
        super.finish();
        Motion.back(this);
    }
}
