package com.ast2012a.clinicalmaster;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.animation.ValueAnimator;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

public final class Motion {

    private static final Map<Activity, Boolean> PREPARED = new WeakHashMap<>();

    private Motion() {}

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity a, Bundle b) {}

            @Override
            public void onActivityStarted(Activity a) {
                prepare(a);
            }

            @Override
            public void onActivityResumed(Activity a) {}

            @Override
            public void onActivityPaused(Activity a) {}

            @Override
            public void onActivityStopped(Activity a) {}

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}

            @Override
            public void onActivityDestroyed(Activity a) {
                PREPARED.remove(a);
            }
        });
    }

    private static boolean isSystemScreen(Activity a) {
        return a instanceof SplashActivity || a instanceof CrashActivity;
    }

    private static boolean isManualScreen(Activity a) {
        return a instanceof PdfViewerActivity || a instanceof AiAssistantActivity;
    }

    private static boolean animationsEnabled(Activity a) {
        try {
            return Settings.Global.getFloat(a.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Exception e) {
            return true;
        }
    }

    private static void prepare(final Activity a) {
        if (isSystemScreen(a) || PREPARED.containsKey(a)) return;
        final View content = a.findViewById(android.R.id.content);
        if (content == null) return;
        PREPARED.put(a, Boolean.TRUE);

        injectBanner(a, content);
        bindEmptyScene(a);

        if (isManualScreen(a)) return;
        final ViewTreeObserver vto = content.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                ViewTreeObserver o = content.getViewTreeObserver();
                if (o.isAlive()) o.removeOnPreDrawListener(this);
                try {
                    pressify(content);
                    if (animationsEnabled(a)) {
                        entrance(content);
                        decorate(content);
                    }
                } catch (Throwable ignored) {
                }
                return true;
            }
        });
    }

    private static int bannerScene(Activity a) {
        if (a instanceof SettingsActivity) return IllustrationView.GEARS;
        if (a instanceof UserGuideActivity) return IllustrationView.BOOK;
        if (a instanceof ScheduleActivity) return IllustrationView.CALENDAR;
        if (a instanceof PatientDetailActivity) return IllustrationView.WALK;
        if (a instanceof AddEditPatientActivity) return IllustrationView.PATIENTS;
        if (a instanceof PrivacyPolicyActivity) return IllustrationView.SHIELD;
        if (a instanceof MainActivity) return IllustrationView.PULSE;
        return -1;
    }

    private static int emptyScene(Activity a) {
        if (a instanceof FavoritesActivity) return IllustrationView.STAR;
        if (a instanceof CloudStorageActivity) return IllustrationView.CLOUD;
        if (a instanceof PdfViewerActivity) return IllustrationView.DOC;
        if (a instanceof MyCasesActivity) return IllustrationView.PULSE;
        if (a instanceof PatientsActivity) return IllustrationView.PATIENTS;
        if (a instanceof TreatmentProgramsActivity) return IllustrationView.EXERCISE;
        return IllustrationView.SEARCH;
    }

    private static void bindEmptyScene(Activity a) {
        View v = a.findViewById(R.id.empty_illustration);
        if (v instanceof IllustrationView) ((IllustrationView) v).setScene(emptyScene(a));
    }

    private static void injectBanner(Activity a, View content) {
        int scene = bannerScene(a);
        if (scene < 0 || !(content instanceof ViewGroup)) return;
        ScrollView sv = findScroll((ViewGroup) content);
        if (sv == null || sv.getChildCount() == 0) return;
        View child = sv.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout box = (LinearLayout) child;
        if (box.getOrientation() != LinearLayout.VERTICAL) return;
        if (box.getChildCount() > 0 && box.getChildAt(0) instanceof IllustrationView) return;
        IllustrationView ill = new IllustrationView(a);
        ill.setScene(scene);
        box.addView(ill, 0, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 118)));
    }

    private static ScrollView findScroll(ViewGroup root) {
        ArrayDeque<ViewGroup> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            ViewGroup g = q.poll();
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c instanceof ScrollView) return (ScrollView) c;
                if (c instanceof ViewGroup && !(c instanceof RecyclerView) && !(c instanceof NestedScrollView)) {
                    q.add((ViewGroup) c);
                }
            }
        }
        return null;
    }

    private static boolean skipGroup(ViewGroup g) {
        return g instanceof androidx.appcompat.widget.Toolbar
                || g instanceof androidx.appcompat.widget.ActionMenuView
                || g instanceof RecyclerView
                || g instanceof AdapterView
                || g instanceof AbsListView;
    }

    private static int visibleCount(ViewGroup g) {
        int n = 0;
        for (int i = 0; i < g.getChildCount(); i++) {
            if (g.getChildAt(i).getVisibility() == View.VISIBLE) n++;
        }
        return n;
    }

    private static ViewGroup staggerTarget(ViewGroup root) {
        ArrayDeque<ViewGroup> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            ViewGroup g = q.poll();
            if (!skipGroup(g) && visibleCount(g) >= 3) return g;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c instanceof ViewGroup && !skipGroup((ViewGroup) c)
                        && c.getVisibility() == View.VISIBLE) {
                    q.add((ViewGroup) c);
                }
            }
        }
        return null;
    }

    private static void entrance(View content) {
        if (!(content instanceof ViewGroup)) return;
        ViewGroup target = staggerTarget((ViewGroup) content);
        if (target == null) return;
        float dy = Ui.dp(content.getContext(), 22);
        int n = 0;
        for (int i = 0; i < target.getChildCount(); i++) {
            View c = target.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getWidth() == 0 || c.getAlpha() < 1f) continue;
            c.setAlpha(0f);
            c.setTranslationY(dy);
            c.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(40L + Math.min(n, 10) * 45L)
                    .setDuration(380)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
            n++;
        }
    }

    private static void pressify(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            if (g instanceof RecyclerView || g instanceof AdapterView || g instanceof AbsListView) return;
            for (int i = 0; i < g.getChildCount(); i++) pressify(g.getChildAt(i));
        }
        if (v.getTag(R.id.tag_motion) != null) return;
        if (v instanceof EditText || v instanceof SeekBar || v instanceof RecyclerView) return;
        if (!v.isClickable() || !v.hasOnClickListeners()) return;
        if (v.getStateListAnimator() != null) return;
        v.setTag(R.id.tag_motion, Boolean.TRUE);
        Ui.applyPressFeedback(v);
    }

    private static void decorate(View v) {
        if (v instanceof androidx.appcompat.widget.Toolbar) {
            popToolbar((ViewGroup) v);
            return;
        }
        if (v instanceof RecyclerView) {
            RecyclerView rv = (RecyclerView) v;
            if (rv.getLayoutAnimation() == null) {
                rv.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(rv.getContext(), R.anim.layout_fall_stagger));
                rv.scheduleLayoutAnimation();
            }
            return;
        }
        if (v instanceof com.google.android.material.floatingactionbutton.FloatingActionButton
                && v.getVisibility() == View.VISIBLE && v.getTag(R.id.tag_breathe) == null) {
            breathe(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) decorate(g.getChildAt(i));
        }
    }

    private static void popToolbar(ViewGroup tb) {
        java.util.ArrayList<View> icons = new java.util.ArrayList<>();
        collectIcons(tb, icons);
        for (int i = 0; i < icons.size(); i++) {
            View ic = icons.get(i);
            ic.setAlpha(0f);
            ic.setScaleX(0.3f);
            ic.setScaleY(0.3f);
            ic.setRotation(-40f);
            ic.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f)
                    .setStartDelay(160L + i * 80L).setDuration(380)
                    .setInterpolator(new OvershootInterpolator(2.2f)).start();
        }
    }

    private static void collectIcons(ViewGroup g, java.util.List<View> out) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof android.widget.ImageButton || c.getClass().getSimpleName().equals("ActionMenuItemView")) {
                out.add(c);
            } else if (c instanceof ViewGroup) {
                collectIcons((ViewGroup) c, out);
            }
        }
    }

    private static void breathe(final View v) {
        v.setTag(R.id.tag_breathe, Boolean.TRUE);
        final ValueAnimator a = ValueAnimator.ofFloat(1f, 1.07f);
        a.setDuration(1300);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.addUpdateListener(an -> {
            float s = (Float) an.getAnimatedValue();
            v.setScaleX(s);
            v.setScaleY(s);
        });
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                if (!a.isStarted()) a.start();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                a.cancel();
            }
        });
        if (v.isAttachedToWindow()) a.start();
    }
}
