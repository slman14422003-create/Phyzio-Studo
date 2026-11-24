package com.ast2012a.clinicalmaster;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
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

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

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
            Context ctx = tv.getContext();
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ContextCompat.getColor(ctx, R.color.selection_bar_bg));
            bg.setCornerRadius(dp(22));
            row.setBackground(bg);
            int rowPad = dp(6);
            row.setPadding(rowPad, dp(2), rowPad, dp(2));

            boolean editable = tv instanceof EditText && ((EditText) tv).getKeyListener() != null;
            boolean hasSel = tv.getSelectionStart() != tv.getSelectionEnd();
            int len = tv.getText() == null ? 0 : tv.getText().length();
            boolean allSelected = Math.min(tv.getSelectionStart(), tv.getSelectionEnd()) <= 0
                    && Math.max(tv.getSelectionStart(), tv.getSelectionEnd()) >= len;

            // الإجراءات بنفس الترتيب القديم: قص، نسخ، لصق، تحديد الكل، مشاركة
            List<String> labels = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            if (editable && hasSel) {
                labels.add("قص");
                actions.add(() -> {
                    tv.onTextContextMenuItem(android.R.id.cut);
                    finish();
                });
            }
            if (hasSel) {
                labels.add("نسخ");
                actions.add(() -> {
                    tv.onTextContextMenuItem(android.R.id.copy);
                    finish();
                });
            }
            if (editable && clipboardHasText()) {
                labels.add("لصق");
                actions.add(() -> {
                    tv.onTextContextMenuItem(android.R.id.paste);
                    finish();
                });
            }
            if (!allSelected && len > 0) {
                labels.add("تحديد الكل");
                actions.add(() -> {
                    tv.onTextContextMenuItem(android.R.id.selectAll);
                    // نعيد بناء الشريط ليعكس الحالة الجديدة
                    if (mode != null) show();
                });
            }
            if (hasSel) {
                labels.add("مشاركة");
                actions.add(this::share);
            }

            int n = labels.size();
            if (n == 0) return row;

            // عرض متساوٍ لكل العناصر: الأعرض نصًا، بحد أدنى 56dp، وبحيث يتسع الشريط للشاشة
            Resources res = ctx.getResources();
            int hPad = res.getDimensionPixelSize(R.dimen.space_12);
            int minW = res.getDimensionPixelSize(R.dimen.selection_item_min_width);
            int dividerW = dp(1);
            int screenW = res.getDisplayMetrics().widthPixels;
            int avail = screenW - 2 * dp(8) - 2 * rowPad - (n - 1) * dividerW;
            float sizePx = res.getDimension(R.dimen.text_body);
            int itemW = equalItemWidth(labels, sizePx, hPad, minW);
            if (itemW * n > avail) {
                // لا يتسع بالحجم العادي: ننتقل لحجم الخط الأصغر من الرموز ثم نقيّد العرض بالمتاح
                sizePx = res.getDimension(R.dimen.text_caption);
                itemW = Math.min(equalItemWidth(labels, sizePx, hPad, minW), Math.max(minW, avail / n));
            }

            for (int i = 0; i < n; i++) {
                if (i > 0) addDivider(row, dividerW);
                addButton(row, labels.get(i), actions.get(i), sizePx, itemW, hPad);
            }
            return row;
        }

        private int equalItemWidth(List<String> labels, float sizePx, int hPad, int minW) {
            TextPaint paint = new TextPaint();
            paint.setTextSize(sizePx);
            float widest = 0f;
            for (String l : labels) widest = Math.max(widest, paint.measureText(l));
            return Math.max(minW, (int) Math.ceil(widest) + 2 * hPad);
        }

        private void addDivider(LinearLayout row, int widthPx) {
            View d = new View(tv.getContext());
            d.setBackgroundColor(ContextCompat.getColor(tv.getContext(), R.color.selection_bar_divider));
            row.addView(d, new LinearLayout.LayoutParams(widthPx, dp(24)));
        }

        private void addButton(LinearLayout row, String label, Runnable action,
                               float sizePx, int widthPx, int hPad) {
            Context ctx = tv.getContext();
            TextView b = new TextView(ctx);
            b.setText(label);
            b.setTextColor(ContextCompat.getColor(ctx, R.color.selection_bar_text));
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
            b.setSingleLine(true);
            b.setEllipsize(TextUtils.TruncateAt.END);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(ctx.getResources().getDimensionPixelSize(R.dimen.touch_target_min));
            b.setPadding(hPad, dp(11), hPad, dp(11));
            b.setBackgroundResource(android.R.drawable.list_selector_background);
            b.setOnClickListener(v -> action.run());
            row.addView(b, new LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT));
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
