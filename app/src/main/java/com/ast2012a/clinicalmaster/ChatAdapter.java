package com.ast2012a.clinicalmaster;

import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    public interface OnSaveAsCaseListener {
        void onSaveAsCase(String aiText);
    }

    public interface OnRegenerateListener {
        void onRegenerate(String originalQuery);
    }

    private final List<ChatMessage> messages = new ArrayList<>();
    private final Set<ChatMessage> fresh = Collections.newSetFromMap(new IdentityHashMap<ChatMessage, Boolean>());
    private final OnSaveAsCaseListener saveListener;
    private OnRegenerateListener regenerateListener;
    private BurstView burst;

    public ChatAdapter(OnSaveAsCaseListener saveListener) {
        this.saveListener = saveListener;
    }

    public void setOnRegenerateListener(OnRegenerateListener l) {
        this.regenerateListener = l;
    }

    public void setBurstView(BurstView b) {
        this.burst = b;
    }

    public void addMessage(ChatMessage m) {
        fresh.add(m);
        messages.add(m);
        notifyItemInserted(messages.size() - 1);
    }

    public void setMessages(List<ChatMessage> newMessages) {
        fresh.clear();
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void clearAll() {
        fresh.clear();
        messages.clear();
        notifyDataSetChanged();
    }

    public int size() {
        return messages.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_bubble, parent, false);
        return new ViewHolder(v);
    }

    private void reset(ViewHolder h) {
        if (h.typing != null) {
            h.typing.cancel();
            h.typing = null;
        }
        View[] views = {h.itemView, h.userText, h.aiBlock, h.actions, h.spark, h.copyBtn,
                h.regenerateBtn, h.saveBtn, h.source};
        for (View v : views) {
            v.animate().cancel();
            v.setAlpha(1f);
            v.setScaleX(1f);
            v.setScaleY(1f);
            v.setTranslationX(0f);
            v.setTranslationY(0f);
            v.setRotation(0f);
        }
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        reset(holder);
        super.onViewRecycled(holder);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final ChatMessage m = messages.get(position);
        boolean isUser = m.role == ChatMessage.ROLE_USER;
        final Context ctx = holder.itemView.getContext();
        reset(holder);

        holder.userText.setVisibility(isUser ? View.VISIBLE : View.GONE);
        holder.aiBlock.setVisibility(isUser ? View.GONE : View.VISIBLE);

        if (isUser) {
            holder.userText.setText(m.text);
            if (fresh.remove(m)) animateUser(holder);
            return;
        }

        holder.aiText.setText(m.text);

        if (m.sourceLabel != null && !m.sourceLabel.isEmpty()) {
            holder.source.setVisibility(View.VISIBLE);
            holder.source.setText(m.sourceLabel + (m.sourceUrl != null ? "  ↗" : ""));
            holder.source.setOnClickListener(v -> {
                if (m.sourceUrl == null) return;
                AiMotion.bounce(v);
                try {
                    ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(m.sourceUrl)));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(ctx, "لا يوجد متصفح متاح لفتح الرابط.", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            holder.source.setVisibility(View.GONE);
        }

        if (saveListener != null) {
            holder.saveBtn.setVisibility(View.VISIBLE);
            holder.saveBtn.setOnClickListener(v -> {
                AiMotion.bounce(v);
                if (burst != null) burst.burstAt(v, 8);
                saveListener.onSaveAsCase(m.text);
            });
        } else {
            holder.saveBtn.setVisibility(View.GONE);
        }

        holder.copyBtn.setVisibility(View.VISIBLE);
        holder.copyBtn.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("ai_answer", m.text));
                Toast.makeText(ctx, "تم نسخ الرد.", Toast.LENGTH_SHORT).show();
            }
            AiMotion.bounce(v);
            if (burst != null) {
                burst.ringAt(v);
                burst.burstAt(v, 6);
            }
            if (v instanceof ImageView) {
                final ImageView iv = (ImageView) v;
                iv.setImageResource(R.drawable.ic_check);
                iv.postDelayed(() -> iv.setImageResource(R.drawable.ic_copy_out), 1300);
            }
        });

        if (regenerateListener != null && m.relatedQuery != null) {
            holder.regenerateBtn.setVisibility(View.VISIBLE);
            holder.regenerateBtn.setOnClickListener(v -> {
                AiMotion.spin(v);
                if (burst != null) burst.ringAt(v);
                regenerateListener.onRegenerate(m.relatedQuery);
            });
        } else {
            holder.regenerateBtn.setVisibility(View.GONE);
        }

        if (fresh.remove(m)) animateAi(holder);
    }

    private void animateUser(final ViewHolder h) {
        final View v = h.userText;
        v.post(() -> {
            boolean rtl = v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            v.setPivotX(rtl ? v.getWidth() : 0f);
            v.setPivotY(v.getHeight());
            v.setAlpha(0f);
            v.setScaleX(0.6f);
            v.setScaleY(0.6f);
            v.setTranslationY(Ui.dp(v.getContext(), 46));
            v.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(420).setInterpolator(new OvershootInterpolator(1.15f)).start();
        });
    }

    private void animateAi(final ViewHolder h) {
        final View[] tools = {h.spark, h.copyBtn, h.regenerateBtn, h.saveBtn};
        for (View t : tools) if (t.getVisibility() == View.VISIBLE) t.setAlpha(0f);
        h.source.setAlpha(0f);
        AiMotion.riseIn(h.aiBlock, 0, 14, 340);
        h.typing = AiMotion.reveal(h.aiText, 2400, () -> {
            long d = 0;
            for (View t : tools) {
                if (t.getVisibility() != View.VISIBLE) continue;
                AiMotion.popIn(t, d);
                d += 70;
            }
            if (h.source.getVisibility() == View.VISIBLE) AiMotion.riseIn(h.source, d, 8, 300);
        });
        h.spark.animate().rotationBy(360f).setDuration(900).start();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView userText;
        final View aiBlock;
        final TextView aiText;
        final TextView source;
        final TextView saveBtn;
        final View copyBtn;
        final View regenerateBtn;
        final View actions;
        final View spark;
        ValueAnimator typing;

        ViewHolder(View itemView) {
            super(itemView);
            userText = itemView.findViewById(R.id.bubble_user_text);
            aiBlock = itemView.findViewById(R.id.bubble_ai_block);
            aiText = itemView.findViewById(R.id.bubble_ai_text);
            source = itemView.findViewById(R.id.bubble_source);
            saveBtn = itemView.findViewById(R.id.bubble_save_case);
            copyBtn = itemView.findViewById(R.id.bubble_copy);
            regenerateBtn = itemView.findViewById(R.id.bubble_regenerate);
            actions = itemView.findViewById(R.id.bubble_actions);
            spark = itemView.findViewById(R.id.bubble_spark);
        }
    }
}
