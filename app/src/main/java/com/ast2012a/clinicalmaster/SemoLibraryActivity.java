package com.ast2012a.clinicalmaster;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SemoLibraryActivity extends AppCompatActivity {

    public static final String EXTRA_PREFILL_QUESTION = "prefill_question";

    private static final int TAB_APPROVED = 0;
    private static final int TAB_DRAFTS = 1;
    private static final int TAB_UNANSWERED = 2;

    private static final class Row {
        SemoCard card;
        SemoLibrary.Unanswered unanswered;
    }

    private TabLayout tabs;
    private RecyclerView list;
    private View emptyView;
    private TextView emptyTitle;
    private TextView suggestBtn;
    private TextInputEditText searchField;
    private RowAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_semo_library);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tabs = findViewById(R.id.semo_tabs);
        list = findViewById(R.id.semo_list);
        emptyView = findViewById(R.id.semo_empty);
        // الحالة الفارغة الموحّدة: نستخدم العنوان فقط (نص الحالة) ونُخفي الشرح والزر
        emptyTitle = emptyView.findViewById(R.id.empty_title);
        emptyView.findViewById(R.id.empty_body).setVisibility(View.GONE);
        emptyView.findViewById(R.id.empty_action).setVisibility(View.GONE);
        suggestBtn = findViewById(R.id.semo_suggest_btn);
        searchField = findViewById(R.id.search_field);
        searchField.setHint(R.string.semo_search_hint);

        adapter = new RowAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                reload();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                reload();
            }
        });

        findViewById(R.id.semo_fab_add).setOnClickListener(v -> showEditor(null, null));
        suggestBtn.setOnClickListener(v -> suggestDrafts());

        String prefill = getIntent().getStringExtra(EXTRA_PREFILL_QUESTION);
        if (prefill != null && !prefill.trim().isEmpty()) {
            getIntent().removeExtra(EXTRA_PREFILL_QUESTION);
            showEditor(null, prefill);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void reload() {
        int tab = tabs.getSelectedTabPosition();
        String q = SemoSearch.normalize(searchField.getText() == null ? "" : searchField.getText().toString());
        List<Row> rows = new ArrayList<>();

        if (tab == TAB_UNANSWERED) {
            for (SemoLibrary.Unanswered u : SemoLibrary.listUnanswered(this)) {
                if (!q.isEmpty() && !SemoSearch.normalize(u.question).contains(q)) continue;
                Row r = new Row();
                r.unanswered = u;
                rows.add(r);
            }
        } else {
            SemoCard.Status st = tab == TAB_APPROVED ? SemoCard.Status.APPROVED : SemoCard.Status.DRAFT;
            List<SemoCard> cards = SemoLibrary.list(this, st);
            cards.sort((a, b) -> Long.compare(
                    st == SemoCard.Status.APPROVED ? b.approvedAt : b.createdAt,
                    st == SemoCard.Status.APPROVED ? a.approvedAt : a.createdAt));
            for (SemoCard c : cards) {
                if (!q.isEmpty()) {
                    String hay = SemoSearch.normalize(c.question + " " + android.text.TextUtils.join(" ", c.keywords)
                            + " " + c.answer + " " + c.source);
                    if (!hay.contains(q)) continue;
                }
                Row r = new Row();
                r.card = c;
                rows.add(r);
            }
        }

        suggestBtn.setVisibility(tab == TAB_DRAFTS ? View.VISIBLE : View.GONE);
        adapter.setRows(rows);
        if (rows.isEmpty()) {
            int res = !q.isEmpty() ? R.string.semo_no_results
                    : tab == TAB_APPROVED ? R.string.semo_empty_approved
                    : tab == TAB_DRAFTS ? R.string.semo_empty_drafts : R.string.semo_empty_unanswered;
            emptyTitle.setText(res);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    private static List<String> parseKeywords(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String part : raw.split("[,\u060C;\u061B\\n]+")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private void showEditor(final SemoCard existing, String prefillQuestion) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_semo_card, null);
        final AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();

        TextView title = view.findViewById(R.id.semo_dlg_title);
        final TextInputEditText question = view.findViewById(R.id.semo_dlg_question);
        final TextInputEditText keywords = view.findViewById(R.id.semo_dlg_keywords);
        final TextInputEditText answer = view.findViewById(R.id.semo_dlg_answer);
        final TextInputEditText source = view.findViewById(R.id.semo_dlg_source);
        TextView history = view.findViewById(R.id.semo_dlg_history);

        title.setText(existing == null ? R.string.semo_new_card : R.string.semo_edit_card);
        if (existing != null) {
            question.setText(existing.question);
            keywords.setText(android.text.TextUtils.join(", ", existing.keywords));
            answer.setText(existing.answer);
            source.setText(existing.source);
            if (!existing.history.isEmpty()) {
                history.setText(getString(R.string.semo_history_meta, existing.history.size()));
                history.setVisibility(View.VISIBLE);
            }
        } else if (prefillQuestion != null) {
            question.setText(prefillQuestion);
        }

        view.findViewById(R.id.semo_dlg_cancel).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.semo_dlg_draft).setOnClickListener(v -> {
            SemoCard saved = persist(existing, question, keywords, answer, source);
            if (existing != null && existing.isApproved()) {
                Toast.makeText(this, R.string.semo_reverted_to_draft, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.semo_saved_draft, Toast.LENGTH_SHORT).show();
            }
            if (saved != null) dialog.dismiss();
            reload();
        });

        view.findViewById(R.id.semo_dlg_approve).setOnClickListener(v -> {
            String q = question.getText() == null ? "" : question.getText().toString().trim();
            String a = answer.getText() == null ? "" : answer.getText().toString().trim();
            if (q.isEmpty() || a.isEmpty()) {
                Toast.makeText(this, R.string.semo_need_question_answer, Toast.LENGTH_LONG).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.semo_approve_confirm_title)
                    .setMessage(R.string.semo_approve_confirm_msg)
                    .setNegativeButton(R.string.semo_cancel, null)
                    .setPositiveButton(R.string.semo_approve_confirm_yes, (d, w) -> {
                        SemoCard saved = persist(existing, question, keywords, answer, source);
                        if (saved != null && SemoLibrary.approve(this, saved.id)) {
                            Toast.makeText(this, R.string.semo_approved_done, Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            reload();
                        }
                    })
                    .show();
        });

        dialog.show();
    }

    private SemoCard persist(SemoCard existing, TextInputEditText question, TextInputEditText keywords,
            TextInputEditText answer, TextInputEditText source) {
        SemoCard c = existing == null ? new SemoCard() : existing.copy();
        c.question = question.getText() == null ? "" : question.getText().toString().trim();
        c.keywords = parseKeywords(keywords.getText() == null ? "" : keywords.getText().toString());
        c.answer = answer.getText() == null ? "" : answer.getText().toString().trim();
        c.source = source.getText() == null ? "" : source.getText().toString().trim();
        if (existing == null) return SemoLibrary.add(this, c);
        return SemoLibrary.update(this, c) ? SemoLibrary.get(this, existing.id) : null;
    }

    private void suggestDrafts() {
        Toast.makeText(this, R.string.semo_suggest_working, Toast.LENGTH_SHORT).show();
        final android.content.Context app = getApplicationContext();
        executor.execute(() -> {
            List<String[]> docs = CloudKnowledgeManager.readCachedDocuments(app);
            List<SemoCard> drafts = new ArrayList<>();
            for (String[] d : docs) {
                for (String chunk : SemoLibrary.chunk(d[1])) {
                    SemoCard c = new SemoCard();
                    c.answer = chunk;
                    c.source = d[0];
                    drafts.add(c);
                }
            }
            final int msg;
            final int added;
            if (docs.isEmpty()) {
                msg = R.string.semo_suggest_no_cache;
                added = 0;
            } else {
                added = SemoLibrary.addDraftsSkippingExisting(app, drafts);
                msg = added > 0 ? R.string.semo_suggest_done : R.string.semo_suggest_none;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, msg == R.string.semo_suggest_done
                        ? getString(msg, added) : getString(msg), Toast.LENGTH_LONG).show();
                reload();
            });
        });
    }

    private void confirmDelete(final Row r) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.semo_delete_confirm)
                .setNegativeButton(R.string.semo_cancel, null)
                .setPositiveButton(R.string.semo_delete, (d, w) -> {
                    if (r.card != null) SemoLibrary.delete(this, r.card.id);
                    else if (r.unanswered != null) SemoLibrary.removeUnanswered(this, r.unanswered.question);
                    reload();
                })
                .show();
    }

    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
        private List<Row> rows = new ArrayList<>();

        void setRows(List<Row> r) {
            rows = r;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_semo_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            final Row r = rows.get(position);
            DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
            if (r.card != null) {
                SemoCard c = r.card;
                h.title.setText(c.question.trim().isEmpty() ? getString(R.string.semo_no_question) : c.question);
                h.body.setText(c.answer);
                h.body.setVisibility(View.VISIBLE);
                StringBuilder meta = new StringBuilder();
                if (!c.source.isEmpty()) meta.append(c.source);
                if (c.isApproved()) {
                    if (meta.length() > 0) meta.append(" · ");
                    meta.append(getString(R.string.semo_approved_meta, df.format(new Date(c.approvedAt))));
                }
                h.meta.setText(meta.toString());
                h.itemView.setOnClickListener(v -> showEditor(r.card, null));
            } else {
                SemoLibrary.Unanswered u = r.unanswered;
                h.title.setText(u.question);
                h.body.setVisibility(View.GONE);
                h.meta.setText(getString(R.string.semo_unanswered_meta, u.count, df.format(new Date(u.lastAsked))));
                h.itemView.setOnClickListener(v -> showEditor(null, u.question));
            }
            h.delete.setOnClickListener(v -> confirmDelete(r));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView body;
            final TextView meta;
            final View delete;

            Holder(View v) {
                super(v);
                title = v.findViewById(R.id.semo_row_title);
                body = v.findViewById(R.id.semo_row_body);
                meta = v.findViewById(R.id.semo_row_meta);
                delete = v.findViewById(R.id.semo_row_delete);
            }
        }
    }
}
