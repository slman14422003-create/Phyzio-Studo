package com.ast2012a.clinicalmaster;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Layout;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * شريط "قص / نسخ / لصق / تحديد الكل / مشاركة" خاص بالتطبيق بدل شريط النظام
 * (الذي كان يظهر ضيّقًا وملتصقًا بمقابض التحديد في الواجهة العربية).
 *
 * الفكرة: نُبقي وضع التحديد الأصلي (فتبقى مقابض السحب شغّالة) لكن نُفرغ قائمته
 * فيختفي شريط النظام، ونعرض بدله PopupWindow بأزرار عربية بتباعد مضبوط فوق التحديد.
 * يُركَّب تلقائيًا على كل EditText ونص قابل للتحديد داخل أي شاشة (Activity).
 * ملاحظة: النوافذ الحوارية (Dialog) غير مشمولة حاليًا.
 */
final class SelectionBar {

    private SelectionBar() {}

    static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {
                a.getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(
                        (oldFocus, newFocus) -> attachIfNeeded(newFocus));
            }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }

    static void attachIfNeeded(View v) {
        if (!(v instanceof TextView)) return;
        TextView tv = (TextView) v;
        if (!(tv instanceof EditText) && !tv.isTextSelectable()) return;
        if (tv.getCustomSelectionActionModeCallback() instanceof Callback) return;
        if (tv.getCustomSelectionActionModeCallback() != null) return; // لا نكسر شريطًا مخصصًا موجودًا
        tv.setCustomSelectionActionModeCallback(new Callback(tv));
    }

    private static final class Callback implements ActionMode.Callback {
        private final TextView tv;
        private PopupWindow popup;
        private LinearLayout bar;
        private ViewTreeObserver.OnPreDrawListener drawListener;
        private ActionMode mode;
        private int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;

        Callback(TextView tv) {
            this.tv = tv;
        }

        @Override
        public boolean onCreateActionMode(ActionMode m, Menu menu) {
            mode = m;
            menu.clear();
            show();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode m, Menu menu) {
            menu.clear(); // قائمة فارغة = شريط النظام لا يظهر، والمقابض تبقى
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode m, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode m) {
            dismiss();
            mode = null;
        }

        // ---------------------------------------------------------------- الواجهة

        private int dp(float v) {
            return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                    tv.getResources().getDisplayMetrics()));
        }

        private void show() {
            dismiss();
            bar = buildBar();
            popup = new PopupWindow(bar, ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, false);
            popup.setOutsideTouchable(false);
            popup.setElevation(dp(8));
            popup.setBackgroundDrawable(null);
            lastX = lastY = Integer.MIN_VALUE;
            bar.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int[] p = computePosition();
            popup.showAtLocation(tv, Gravity.NO_GRAVITY, p[0], p[1]);
            lastX = p[0];
            lastY = p[1];
            drawListener = () -> {
                reposition();
                return true;
            };
            tv.getViewTreeObserver().addOnPreDrawListener(drawListener);
        }

        private void dismiss() {
            if (drawListener != null) {
                ViewTreeObserver vto = tv.getViewTreeObserver();
                if (vto.isAlive()) vto.removeOnPreDrawListener(drawListener);
                drawListener = null;
            }
            if (popup != null) {
                try {
                    popup.dismiss();
                } catch (Exception ignored) {
                }
                popup = null;
            }
        }

        private LinearLayout buildBar() {
            LinearLayout row = new LinearLayout(tv.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF2262A31);
            bg.setCornerRadius(dp(22));
            row.setBackground(bg);
            row.setPadding(dp(6), dp(2), dp(6), dp(2));

            boolean editable = tv instanceof EditText && ((EditText) tv).getKeyListener() != null;
            boolean hasSel = tv.getSelectionStart() != tv.getSelectionEnd();
            int len = tv.getText() == null ? 0 : tv.getText().length();
            boolean allSelected = Math.min(tv.getSelectionStart(), tv.getSelectionEnd()) <= 0
                    && Math.max(tv.getSelectionStart(), tv.getSelectionEnd()) >= len;

            if (editable && hasSel) addButton(row, "قص", () -> {
                tv.onTextContextMenuItem(android.R.id.cut);
                finish();
            });
            if (hasSel) addButton(row, "نسخ", () -> {
                tv.onTextContextMenuItem(android.R.id.copy);
                finish();
            });
            if (editable && clipboardHasText()) addButton(row, "لصق", () -> {
                tv.onTextContextMenuItem(android.R.id.paste);
                finish();
            });
            if (!allSelected && len > 0) addButton(row, "تحديد الكل", () -> {
                tv.onTextContextMenuItem(android.R.id.selectAll);
                // نعيد بناء الشريط ليعكس الحالة الجديدة
                if (mode != null) show();
            });
            if (hasSel) addButton(row, "مشاركة", this::share);
            return row;
        }

        private void addButton(LinearLayout row, String label, Runnable action) {
            if (row.getChildCount() > 0) {
                View d = new View(tv.getContext());
                d.setBackgroundColor(0x33FFFFFF);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(1), dp(18));
                row.addView(d, dl);
            }
            TextView b = new TextView(tv.getContext());
            b.setText(label);
            b.setTextColor(Color.WHITE);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            b.setSingleLine(true);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(14), dp(11), dp(14), dp(11));
            b.setBackgroundResource(android.R.drawable.list_selector_background);
            b.setOnClickListener(v -> action.run());
            row.addView(b, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        private boolean clipboardHasText() {
            ClipboardManager cm = (ClipboardManager) tv.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return false;
            ClipDescription d = cm.getPrimaryClipDescription();
            if (d == null) return false;
            ClipData clip = cm.getPrimaryClip();
            return clip != null && clip.getItemCount() > 0
                    && (d.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                    || d.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML));
        }

        private void share() {
            int s = Math.min(tv.getSelectionStart(), tv.getSelectionEnd());
            int e = Math.max(tv.getSelectionStart(), tv.getSelectionEnd());
            if (s < 0 || e <= s) return;
            CharSequence text = tv.getText().subSequence(s, e);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, text.toString());
            Intent chooser = Intent.createChooser(send, null);
            if (!(tv.getContext() instanceof Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                tv.getContext().startActivity(chooser);
            } catch (Exception ignored) {
            }
            finish();
        }

        private void finish() {
            if (mode != null) mode.finish();
        }

        // ---------------------------------------------------------------- الموضع

        /** يحسب موضع الشريط (إحداثيات الشاشة) فوق بداية التحديد، أو تحته لو ما في مساحة. */
        private int[] computePosition() {
            Layout layout = tv.getLayout();
            int[] loc = new int[2];
            tv.getLocationOnScreen(loc);
            int w = bar.getMeasuredWidth();
            int h = bar.getMeasuredHeight();
            int screenW = tv.getResources().getDisplayMetrics().widthPixels;
            int margin = dp(8);
            if (layout == null) {
                return new int[]{Math.max(margin, (screenW - w) / 2), loc[1] - h - margin};
            }
            int s = Math.min(tv.getSelectionStart(), tv.getSelectionEnd());
            int e = Math.max(tv.getSelectionStart(), tv.getSelectionEnd());
            if (s < 0) s = 0;
            int len = layout.getText().length();
            if (s > len) s = len;
            if (e > len) e = len;
            int lineS = layout.getLineForOffset(s);
            int lineE = layout.getLineForOffset(e);
            float xs = layout.getPrimaryHorizontal(s);
            float xe = layout.getPrimaryHorizontal(e);
            float xMid = lineS == lineE ? (xs + xe) / 2f : xs;
            int cx = loc[0] + tv.getTotalPaddingLeft() - tv.getScrollX() + Math.round(xMid);
            int topY = loc[1] + tv.getTotalPaddingTop() - tv.getScrollY() + layout.getLineTop(lineS);
            int bottomY = loc[1] + tv.getTotalPaddingTop() - tv.getScrollY() + layout.getLineBottom(lineE);

            int x = Math.min(Math.max(margin, cx - w / 2), Math.max(margin, screenW - w - margin));
            int y = topY - h - dp(10);
            Rect vis = new Rect();
            tv.getWindowVisibleDisplayFrame(vis);
            if (y < vis.top + margin) {
                y = bottomY + dp(44); // تحت التحديد مع ترك مجال لمقبض السحب
            }
            return new int[]{x, y};
        }

        private void reposition() {
            if (popup == null || !popup.isShowing()) return;
            int[] p = computePosition();
            if (p[0] == lastX && p[1] == lastY) return;
            lastX = p[0];
            lastY = p[1];
            popup.update(p[0], p[1], -1, -1);
        }
    }
}
