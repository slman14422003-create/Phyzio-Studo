package com.ast2012a.clinicalmaster;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiAssistantActivity extends AppCompatActivity {
    private RecyclerView chatList;
    private ChatAdapter adapter;
    private TextInputEditText input;
    private View typingIndicator;
    private TextView typingText;
    private View emptyState;
    private LinearLayout quickPromptsRow;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String contextPatientId;
    private boolean requestInFlight;
    private boolean deepMode;
    private FloatingActionButton sendButton;
    private SemoOrbView orb;
    private AmbientView ambient;
    private BurstView burst;
    private View composer;
    private View composerGlow;
    private View toolbarSpark;
    private TextView quickTitle;
    private boolean emptyShown;
    private boolean promptChosen;
    private boolean lastHasText;
    private ValueAnimator sparkAnim;
    private ValueAnimator glowAnim;
    private ValueAnimator hintAnim;
    private ObjectAnimator sendSpin;
    private ObjectAnimator typingSpin;
    private View typingSparkle;
    private boolean sendBusy;
    private final Handler hintHandler = new Handler(Looper.getMainLooper());
    private CharSequence defaultHint = "";
    private int hintIndex;

    private String contextDocumentName;
    private String contextDocumentText;

    private static final String[] QUICK_PROMPTS = {
            "اشرحلي الفرق بين TENS و EMS",
            "بروتوكول مقترح لآلام أسفل الظهر",
            "احتياطات السلامة العامة قبل أي جلسة",
            "ما هو عرق النسا؟",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_assistant);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_ai_assistant);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        chatList = findViewById(R.id.chat_list);
        input = findViewById(R.id.chat_input);
        typingIndicator = findViewById(R.id.typing_indicator);
        typingText = findViewById(R.id.typing_text);
        emptyState = findViewById(R.id.empty_state);
        quickPromptsRow = findViewById(R.id.quick_prompts_row);
        FloatingActionButton sendBtn = findViewById(R.id.btn_send);
        sendButton = sendBtn;

        typingSparkle = findViewById(R.id.typing_sparkle);
        startSparkleSpin(typingSparkle);

        orb = findViewById(R.id.ai_orb);
        ambient = findViewById(R.id.ai_ambient);
        composer = findViewById(R.id.composer);
        composerGlow = findViewById(R.id.composer_glow);
        toolbarSpark = findViewById(R.id.toolbar_spark);
        quickTitle = findViewById(R.id.quick_prompts_title);
        burst = new BurstView(this);
        addContentView(burst, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        adapter = new ChatAdapter(this::openSaveAsCase);
        adapter.setOnRegenerateListener(this::regenerateAnswer);
        adapter.setOnAddCardListener(q -> {
            Intent i = new Intent(this, SemoLibraryActivity.class);
            i.putExtra(SemoLibraryActivity.EXTRA_PREFILL_QUESTION, q);
            startActivity(i);
            Motion.forward(this);
        });
        adapter.setBurstView(burst);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        chatList.setLayoutManager(lm);
        chatList.setAdapter(adapter);
        LayoutAnimationController controller =
                AnimationUtils.loadLayoutAnimation(this, R.anim.layout_fall_stagger);
        chatList.setLayoutAnimation(controller);

        sendBtn.setOnClickListener(v -> onSendClicked());
        setupComposer();
        startToolbarSpark();

        adapter.setMessages(AiChatStore.load(this));
        if (adapter.getItemCount() > 0) {
            chatList.scrollToPosition(adapter.getItemCount() - 1);
        }

        setupQuickPrompts();
        refreshQuickPromptsVisibility();

        String prefillQuery = getIntent().getStringExtra("prefill_query");
        if (prefillQuery != null && !prefillQuery.isEmpty()) {
            input.setText(prefillQuery);
            input.setSelection(prefillQuery.length());
        }

        contextPatientId = getIntent().getStringExtra("patient_id");
        contextDocumentName = getIntent().getStringExtra("document_name");
        contextDocumentText = getIntent().getStringExtra("document_text");
        bindContextBanner();
        renderSendState(false);
        startHintLoop();
    }

    private void startToolbarSpark() {
        startSparkAnim();
    }

    private void startGlowAnim() {
        if (composerGlow == null || !Motion.animationsEnabled(this)) return;
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofFloat(0.55f, 1f);
        glowAnim.setDuration(900);
        glowAnim.setRepeatMode(ValueAnimator.REVERSE);
        glowAnim.setRepeatCount(ValueAnimator.INFINITE);
        glowAnim.addUpdateListener(a -> composerGlow.setAlpha((Float) a.getAnimatedValue()));
        glowAnim.start();
    }

    private void startSparkAnim() {
        if (toolbarSpark == null || !Motion.animationsEnabled(this)) return;
        if (sparkAnim != null) sparkAnim.cancel();
        sparkAnim = ValueAnimator.ofFloat(0f, 1f);
        sparkAnim.setDuration(3600);
        sparkAnim.setRepeatCount(ValueAnimator.INFINITE);
        sparkAnim.setInterpolator(new LinearInterpolator());
        sparkAnim.addUpdateListener(a -> {
            float p = (Float) a.getAnimatedValue();
            float s = (float) Math.sin(p * Math.PI * 2.0);
            toolbarSpark.setRotation(p * 360f);
            float sc = 1f + 0.16f * s * s;
            toolbarSpark.setScaleX(sc);
            toolbarSpark.setScaleY(sc);
        });
        sparkAnim.start();
    }

    private void setupComposer() {
        defaultHint = input.getHint() == null ? "" : input.getHint();
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                renderSendState(true);
                if (s.length() > 0 && orb != null && before0(s)) orb.kick();
            }
        });
        input.setOnFocusChangeListener((v, focused) -> {
            if (composerGlow == null) return;
            if (glowAnim != null) {
                glowAnim.cancel();
                glowAnim = null;
            }
            composer.animate().scaleX(focused ? 1.012f : 1f).scaleY(focused ? 1.012f : 1f)
                    .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
            if (focused) {
                input.setHint(defaultHint);
                if (Motion.animationsEnabled(this)) startGlowAnim();
                else composerGlow.setAlpha(1f);
            } else {
                composerGlow.animate().alpha(0f).setDuration(200).start();
            }
        });
    }

    private boolean before0(Editable s) {
        return s.length() % 3 == 1;
    }

    private void startHintLoop() {
        hintHandler.removeCallbacksAndMessages(null);
        hintHandler.postDelayed(this::runHintCycle, 2600);
    }

    private void runHintCycle() {
        if (isFinishing() || isDestroyed()) return;
        boolean idle = !input.hasFocus() && (input.getText() == null || input.getText().length() == 0);
        if (!idle) {
            hintHandler.postDelayed(this::runHintCycle, 2000);
            return;
        }
        hintIndex = (hintIndex + 1) % (QUICK_PROMPTS.length + 1);
        final String target = hintIndex == 0 ? String.valueOf(defaultHint) : QUICK_PROMPTS[hintIndex - 1];
        if (hintAnim != null) hintAnim.cancel();
        hintAnim = ValueAnimator.ofInt(0, target.length());
        hintAnim.setDuration(Math.max(300L, target.length() * 40L));
        hintAnim.setInterpolator(new LinearInterpolator());
        hintAnim.addUpdateListener(a -> {
            if (input.hasFocus()) return;
            int n = (Integer) a.getAnimatedValue();
            input.setHint(target.substring(0, n));
        });
        hintAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                hintHandler.postDelayed(AiAssistantActivity.this::runHintCycle, 2400);
            }
        });
        hintAnim.start();
    }

    private void renderSendState(boolean animate) {
        if (sendButton == null) return;
        boolean has = input.getText() != null && input.getText().toString().trim().length() > 0;
        if (requestInFlight) {
            sendButton.animate().cancel();
            sendButton.setAlpha(1f);
            sendButton.setScaleX(1f);
            sendButton.setScaleY(1f);
            if (!sendBusy) {
                sendBusy = true;
                sendButton.setImageResource(R.drawable.ic_refresh);
            }
            if (sendSpin == null && Motion.animationsEnabled(this)) {
                sendSpin = ObjectAnimator.ofFloat(sendButton, "rotation", 0f, 360f);
                sendSpin.setDuration(900);
                sendSpin.setInterpolator(new LinearInterpolator());
                sendSpin.setRepeatCount(ObjectAnimator.INFINITE);
                sendSpin.start();
            }
            return;
        }
        if (sendBusy) {
            sendBusy = false;
            if (sendSpin != null) {
                sendSpin.cancel();
                sendSpin = null;
            }
            sendButton.setRotation(0f);
            sendButton.setImageResource(R.drawable.ic_arrow_up);
            animate = true;
            lastHasText = !has;
        }
        if (has == lastHasText && animate) return;
        lastHasText = has;
        sendButton.animate().cancel();
        if (!animate) {
            sendButton.setAlpha(has ? 1f : 0.45f);
            sendButton.setScaleX(has ? 1f : 0.88f);
            sendButton.setScaleY(has ? 1f : 0.88f);
            return;
        }
        if (has) {
            sendButton.setScaleX(0.7f);
            sendButton.setScaleY(0.7f);
            sendButton.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(340)
                    .setInterpolator(new OvershootInterpolator(3f)).start();
            Ui.tick(sendButton);
        } else {
            sendButton.animate().alpha(0.45f).scaleX(0.88f).scaleY(0.88f).setDuration(220).start();
        }
    }

    private void animateSend() {
        burst.ringAt(sendButton);
        burst.burstAt(sendButton, 12);
        Ui.tick(sendButton);
        sendButton.animate().cancel();
        sendButton.animate().scaleX(0.8f).scaleY(0.8f).setDuration(70).withEndAction(() ->
                sendButton.animate().translationY(-Ui.dp(this, 12)).scaleX(1.15f).scaleY(1.15f).setDuration(110)
                        .withEndAction(() -> {
                            sendButton.setTranslationY(Ui.dp(this, 14));
                            sendButton.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(260)
                                    .setInterpolator(new OvershootInterpolator(2.2f)).start();
                        }).start()).start();
        composer.animate().cancel();
        composer.setScaleX(0.985f);
        composer.setScaleY(0.985f);
        composer.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(2f)).start();
    }

    private void bindContextBanner() {
        View banner = findViewById(R.id.ai_patient_context_banner);
        if (banner == null) return;
        boolean hasPatient = contextPatientId != null && !contextPatientId.isEmpty();
        boolean hasDocument = contextDocumentText != null && !contextDocumentText.trim().isEmpty();

        ImageView icon = findViewById(R.id.ai_context_banner_icon);
        TextView text = findViewById(R.id.ai_context_banner_text);
        if (hasDocument) {
            if (icon != null) icon.setImageResource(R.drawable.ic_pdf);
            if (text != null) {
                String name = contextDocumentName != null && !contextDocumentName.trim().isEmpty()
                        ? contextDocumentName.trim() : "مستند PDF";
                text.setText("مستند \"" + name + "\" مرفق بهذه المحادثة");
            }
        } else if (hasPatient) {
            if (icon != null) icon.setImageResource(R.drawable.ic_users);
            if (text != null) text.setText("ملف المريض مرفق بهذه المحادثة (بدون الاسم أو رقم الهاتف)");
        }
        banner.setVisibility(hasDocument || hasPatient ? View.VISIBLE : View.GONE);
        if (hasDocument || hasPatient) AiMotion.riseIn(banner, 200, -16, 420);
    }

    private void startSparkleSpin(View sparkle) {
        if (sparkle == null || !Motion.animationsEnabled(this)) return;
        if (typingSpin != null) typingSpin.cancel();
        typingSpin = ObjectAnimator.ofFloat(sparkle, "rotation", 0f, 360f);
        typingSpin.setDuration(2400);
        typingSpin.setInterpolator(new android.view.animation.LinearInterpolator());
        typingSpin.setRepeatCount(ObjectAnimator.INFINITE);
        typingSpin.start();
    }

    private void cancelInfiniteAnimators() {
        if (sparkAnim != null) {
            sparkAnim.cancel();
            sparkAnim = null;
            if (toolbarSpark != null) {
                toolbarSpark.setRotation(0f);
                toolbarSpark.setScaleX(1f);
                toolbarSpark.setScaleY(1f);
            }
        }
        if (glowAnim != null) {
            glowAnim.cancel();
            glowAnim = null;
            if (composerGlow != null) composerGlow.setAlpha(input != null && input.hasFocus() ? 1f : 0f);
        }
        if (sendSpin != null) {
            sendSpin.cancel();
            sendSpin = null;
            if (sendButton != null) sendButton.setRotation(0f);
        }
        if (typingSpin != null) {
            typingSpin.cancel();
            typingSpin = null;
            if (typingSparkle != null) typingSparkle.setRotation(0f);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelInfiniteAnimators();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Motion.animationsEnabled(this)) return;
        startSparkAnim();
        startSparkleSpin(typingSparkle);
        if (input != null && input.hasFocus()) startGlowAnim();
        if (requestInFlight) renderSendState(false);
    }

    private void setupQuickPrompts() {
        quickPromptsRow.removeAllViews();
        for (String prompt : QUICK_PROMPTS) {
            TextView row = new TextView(this);
            row.setText(prompt);
            row.setTextColor(getColor(R.color.text_primary));
            row.setTextSize(14.5f);
            row.setTextDirection(View.TEXT_DIRECTION_RTL);
            row.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_suggestion_row);
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_sparkle, 0, 0, 0);
            row.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.claude_orange)));
            row.setCompoundDrawablePadding(Ui.dp(this, 12));
            row.setMinHeight(Ui.dp(this, 52));
            row.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(this, 10);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> chooseQuickPrompt(row, prompt));
            Ui.applyPressFeedback(row);
            quickPromptsRow.addView(row);
        }
    }

    private void chooseQuickPrompt(View chosen, String prompt) {
        if (promptChosen || requestInFlight) return;
        promptChosen = true;
        AiMotion.bounce(chosen);
        burst.ringAt(chosen);
        burst.burstAt(chosen, 14);
        for (int i = 0; i < quickPromptsRow.getChildCount(); i++) {
            View r = quickPromptsRow.getChildAt(i);
            if (r == chosen) continue;
            r.animate().alpha(0f).translationY(Ui.dp(this, 14)).setDuration(220).start();
        }
        chosen.postDelayed(() -> {
            promptChosen = false;
            sendQuery(prompt, true);
        }, 240);
    }

    private void refreshQuickPromptsVisibility() {
        if (adapter.getItemCount() == 0) showEmpty();
        else hideEmpty();
    }

    private void showEmpty() {
        if (emptyShown) return;
        emptyShown = true;
        emptyState.animate().cancel();
        emptyState.setVisibility(View.VISIBLE);
        emptyState.setAlpha(1f);
        emptyState.setTranslationY(0f);
        emptyState.setScaleX(1f);
        emptyState.setScaleY(1f);
        playEmptyIntro();
    }

    private void hideEmpty() {
        if (!emptyShown) {
            emptyState.setVisibility(View.GONE);
            return;
        }
        emptyShown = false;
        emptyState.animate().cancel();
        emptyState.animate().alpha(0f).translationY(-Ui.dp(this, 26)).scaleX(0.96f).scaleY(0.96f)
                .setDuration(240).withEndAction(() -> {
                    if (emptyShown) return;
                    emptyState.setVisibility(View.GONE);
                    emptyState.setAlpha(1f);
                    emptyState.setTranslationY(0f);
                    emptyState.setScaleX(1f);
                    emptyState.setScaleY(1f);
                    for (int i = 0; i < quickPromptsRow.getChildCount(); i++) {
                        View r = quickPromptsRow.getChildAt(i);
                        r.setAlpha(1f);
                        r.setTranslationY(0f);
                    }
                }).start();
    }

    private void playEmptyIntro() {
        orb.animate().cancel();
        orb.setScaleX(0.2f);
        orb.setScaleY(0.2f);
        orb.setAlpha(0f);
        orb.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(720)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
        quickTitle.postDelayed(() -> {
            if (emptyShown) AiMotion.reveal(quickTitle, 900, null);
        }, 260);
        for (int i = 0; i < quickPromptsRow.getChildCount(); i++) {
            View r = quickPromptsRow.getChildAt(i);
            r.animate().cancel();
            r.setAlpha(0f);
            r.setTranslationY(Ui.dp(this, 30));
            r.setScaleX(0.94f);
            r.setScaleY(0.94f);
            r.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay(560L + i * 110L).setDuration(460)
                    .setInterpolator(new DecelerateInterpolator(1.8f)).start();
        }
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_clear_chat) {
            View mi = findViewById(R.id.action_clear_chat);
            if (mi != null) AiMotion.wobble(mi);
            confirmClearChat();
            return true;
        }
        if (item.getItemId() == R.id.action_deep_research) {
            deepMode = !deepMode;
            item.setChecked(deepMode);
            Toast.makeText(this, deepMode ? R.string.ai_deep_on : R.string.ai_deep_off, Toast.LENGTH_LONG).show();
            return true;
        }
        if (item.getItemId() == R.id.action_digest) {
            runDigest();
            return true;
        }
        return false;
    }

    private void runDigest() {
        if (requestInFlight) return;
        if (adapter.getItemCount() == 0) {
            Toast.makeText(this, R.string.ai_digest_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        List<ChatMessage> snapshot = new ArrayList<>(adapter.getMessages());
        setRequestInFlight(true);
        setTypingStage(3);
        executor.execute(() -> {
            try {
                AiOrchestrator.digestConversation(this, snapshot,
                        new AiOrchestrator.StageListener() {
                            @Override public void onClassifying() { }
                            @Override public void onSearching() { }
                            @Override public void onThinking() { runOnUiThread(() -> setTypingStage(3)); }
                        },
                        new AiOrchestrator.ResultCallback() {
                            @Override
                            public void onChatReply(String reply) {
                                deliverReply(new ChatMessage(ChatMessage.ROLE_AI, reply));
                            }

                            @Override
                            public void onGroundedReply(String reply, String sourceLabel, String sourceUrl) {
                                deliverReply(new ChatMessage(ChatMessage.ROLE_AI, reply, sourceLabel, sourceUrl, null));
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    setRequestInFlight(false);
                                    hideTypingIndicator();
                                    Toast.makeText(AiAssistantActivity.this, message, Toast.LENGTH_LONG).show();
                                });
                            }
                        });
            } catch (RuntimeException e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setRequestInFlight(false);
                    hideTypingIndicator();
                    Toast.makeText(AiAssistantActivity.this, R.string.ai_generic_error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void confirmClearChat() {
        if (adapter.getItemCount() == 0) return;
        new ClaudeDialog(this)
                .setTitle("مسح المحادثة")
                .setMessage("هل تريد مسح كل سجل المحادثة مع Semo AI نهائيًا؟")
                .setPositiveButton("مسح", (dialog, which) -> {
                    chatList.animate().alpha(0f).translationY(-Ui.dp(this, 30)).setDuration(240)
                            .withEndAction(() -> {
                                adapter.clearAll();
                                AiChatStore.clear(this);
                                chatList.setAlpha(1f);
                                chatList.setTranslationY(0f);
                                refreshQuickPromptsVisibility();
                                Toast.makeText(this, "تم مسح المحادثة.", Toast.LENGTH_SHORT).show();
                            }).start();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void onSendClicked() {
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        if (text.isEmpty()) {
            AiMotion.shake(composer);
            return;
        }
        if (requestInFlight) return;
        animateSend();
        sendQuery(text, true);
    }

    private void regenerateAnswer(String originalQuery) {
        sendQuery(originalQuery, false);
    }

    private void sendQuery(String text, boolean addUserBubble) {
        if (text == null || text.trim().isEmpty()) return;
        if (requestInFlight) return;

        List<ChatMessage> historySnapshot = new ArrayList<>(adapter.getMessages());
        if (!addUserBubble) {
            while (!historySnapshot.isEmpty()
                    && historySnapshot.get(historySnapshot.size() - 1).role == ChatMessage.ROLE_AI) {
                historySnapshot.remove(historySnapshot.size() - 1);
            }
            while (!historySnapshot.isEmpty()
                    && historySnapshot.get(historySnapshot.size() - 1).role == ChatMessage.ROLE_USER
                    && text.equals(historySnapshot.get(historySnapshot.size() - 1).text)) {
                historySnapshot.remove(historySnapshot.size() - 1);
            }
        }

        if (addUserBubble) {
            adapter.addMessage(new ChatMessage(ChatMessage.ROLE_USER, text));
            AiChatStore.save(this, adapter.getMessages());
            refreshQuickPromptsVisibility();
            chatList.smoothScrollToPosition(adapter.getItemCount() - 1);
            input.setText("");
        }

        setRequestInFlight(true);
        setTypingStage(0);

        executor.execute(() -> {
            try {
                AiOrchestrator.answer(this, text, contextPatientId,
                contextDocumentName, contextDocumentText, historySnapshot,
                new AiOrchestrator.Options(deepMode),
                new AiOrchestrator.StageListener() {
                    @Override public void onClassifying() { runOnUiThread(() -> setTypingStage(0)); }
                    @Override public void onSearching() { runOnUiThread(() -> setTypingStage(1)); }
                    @Override public void onThinking() { runOnUiThread(() -> setTypingStage(2)); }
                },
                new AiOrchestrator.ResultCallback() {
                    @Override
                    public void onChatReply(String reply) {
                        deliverReply(new ChatMessage(ChatMessage.ROLE_AI, reply, null, null, text));
                    }

                    @Override
                    public void onGroundedReply(String reply, String sourceLabel, String sourceUrl) {
                        deliverReply(new ChatMessage(ChatMessage.ROLE_AI, reply, sourceLabel, sourceUrl, text));
                    }

                    @Override
                    public void onApprovedReply(String reply, String sourceLabel, String cardId, long approvedAt) {
                        ChatMessage m = new ChatMessage(ChatMessage.ROLE_AI, reply, sourceLabel, null, text);
                        m.approved = true;
                        m.approvedAt = approvedAt;
                        m.cardId = cardId;
                        deliverReply(m);
                    }

                    @Override
                    public void onNoApprovedAnswer(String message, String question) {
                        ChatMessage m = new ChatMessage(ChatMessage.ROLE_AI, message, null, null, text);
                        m.noAnswer = true;
                        deliverReply(m);
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            setRequestInFlight(false);
                            hideTypingIndicator();
                            Toast.makeText(AiAssistantActivity.this, message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setRequestInFlight(false);
                    hideTypingIndicator();
                    Toast.makeText(AiAssistantActivity.this, R.string.ai_generic_error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void deliverReply(ChatMessage message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            View sp = findViewById(R.id.typing_sparkle);
            if (sp != null && typingIndicator.getVisibility() == View.VISIBLE) burst.burstAt(sp, 16);
            setRequestInFlight(false);
            hideTypingIndicator();
            adapter.addMessage(message);
            AiChatStore.save(AiAssistantActivity.this, adapter.getMessages());
            chatList.smoothScrollToPosition(adapter.getItemCount() - 1);
        });
    }

    private void setRequestInFlight(boolean inFlight) {
        requestInFlight = inFlight;
        if (sendButton != null) sendButton.setEnabled(!inFlight);
        if (orb != null) orb.setThinking(inFlight);
        if (ambient != null) ambient.setBoost(inFlight);
        renderSendState(true);
    }

    private void setTypingStage(int stage) {
        boolean first = typingIndicator.getVisibility() != View.VISIBLE;
        if (first) {
            typingIndicator.animate().cancel();
            typingIndicator.setAlpha(0f);
            typingIndicator.setTranslationY(Ui.dp(this, 12));
            typingIndicator.setVisibility(View.VISIBLE);
            typingIndicator.animate().alpha(1f).translationY(0f).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        String label;
        switch (stage) {
            case 1:
                label = getString(deepMode ? R.string.ai_stage_deep : R.string.ai_stage_searching);
                break;
            case 3:
                label = getString(R.string.ai_stage_digest);
                break;
            case 2:
                label = "يكتب الرد...";
                break;
            default:
                label = "بيفهم قصدك...";
        }
        if (first) {
            typingText.setText(label);
            return;
        }
        if (label.contentEquals(typingText.getText())) return;
        final String next = label;
        typingText.animate().cancel();
        typingText.animate().alpha(0f).translationY(-Ui.dp(this, 6)).setDuration(110).withEndAction(() -> {
            typingText.setText(next);
            typingText.setTranslationY(Ui.dp(this, 6));
            typingText.animate().alpha(1f).translationY(0f).setDuration(160).start();
        }).start();
    }

    private void hideTypingIndicator() {
        typingIndicator.animate().cancel();
        typingIndicator.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> typingIndicator.setVisibility(View.GONE))
                .start();
    }

    private void openSaveAsCase(String aiText) {
        Intent i = new Intent(this, AddEditCaseActivity.class);
        i.putExtra("prefill_explanation", aiText);
        startActivity(i);
        Motion.forward(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelInfiniteAnimators();
        if (hintAnim != null) hintAnim.cancel();
        hintHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    @Override
    public void finish() {
        super.finish();
        Motion.back(this);
    }
}
