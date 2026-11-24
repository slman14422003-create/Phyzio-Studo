package com.ast2012a.clinicalmaster;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation;
import android.view.animation.LayoutAnimationController;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

public final class Motion {

    public static final String PREFS = "settings_prefs";
    public static final String KEY_REDUCE_MOTION = "reduce_motion";

    private static final Map<Activity, Boolean> PREPARED = new WeakHashMap<>();

    private Motion() {}

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity a, Bundle b) {
                if (!animationsEnabled(a)) a.overridePendingTransition(0, 0);
            }

            @Override
            public void onActivityStarted(Activity a) {
                prepare(a);
            }

            @Override
            public void onActivityResumed(Activity a) {}

            @Override
            public void onActivityPaused(Activity a) {
                if (a.isFinishing() && !animationsEnabled(a)) a.overridePendingTransition(0, 0);
            }

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

    public static boolean isReduceMotion(Context c) {
        try {
            SharedPreferences sp = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return sp.getBoolean(KEY_REDUCE_MOTION, false);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean animationsEnabled(Context c) {
        try {
            if (Settings.Global.getFloat(c.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) return false;
        } catch (Exception ignored) {
        }
        return !isReduceMotion(c);
    }

    public static void forward(Activity a) {
        if (animationsEnabled(a)) a.overridePendingTransition(R.anim.nav_forward_enter, R.anim.nav_forward_exit);
        else a.overridePendingTransition(0, 0);
    }

    public static void back(Activity a) {
        if (animationsEnabled(a)) a.overridePendingTransition(R.anim.nav_back_enter, R.anim.nav_back_exit);
        else a.overridePendingTransition(0, 0);
    }

    public static void fade(Activity a) {
        if (animationsEnabled(a)) a.overridePendingTransition(R.anim.splash_fade_in, R.anim.splash_fade_out);
        else a.overridePendingTransition(0, 0);
    }

    public static void fabIn(View fab) {
        if (fab == null || fab.getTag(R.id.tag_fab_intro) != null) return;
        fab.setTag(R.id.tag_fab_intro, Boolean.TRUE);
        if (!animationsEnabled(fab.getContext())) {
            fab.setScaleX(1f);
            fab.setScaleY(1f);
            return;
        }
        fab.setScaleX(0f);
        fab.setScaleY(0f);
        fab.animate().scaleX(1f).scaleY(1f)
                .setDuration(MotionTokens.DUR_NORMAL)
                .setInterpolator(MotionTokens.ENTER)
                .start();
    }

    public static IllustrationView findIllustration(Activity a, int scene) {
        View root = a.findViewById(android.R.id.content);
        return root == null ? null : findIllustration(root, scene);
    }

    private static IllustrationView findIllustration(View v, int scene) {
        if (v instanceof IllustrationView) {
            return ((IllustrationView) v).getScene() == scene ? (IllustrationView) v : null;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                IllustrationView r = findIllustration(g.getChildAt(i), scene);
                if (r != null) return r;
            }
        }
        return null;
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
                    if (animationsEnabled(a)) pressify(content);
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
        float dy = MotionTokens.enterOffsetPx(content.getContext());
        int n = 0;
        for (int i = 0; i < target.getChildCount() && n < MotionTokens.MAX_STAGGERED; i++) {
            View c = target.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getWidth() == 0 || c.getAlpha() < 1f) continue;
            c.setAlpha(0f);
            c.setTranslationY(dy);
            c.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(n * MotionTokens.STAGGER_STEP)
                    .setDuration(MotionTokens.DUR_NORMAL)
                    .setInterpolator(MotionTokens.ENTER)
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
            alignRecycler((RecyclerView) v);
            return;
        }
        if (v instanceof com.google.android.material.floatingactionbutton.FloatingActionButton
                && v.getVisibility() == View.VISIBLE) {
            fabIn(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) decorate(g.getChildAt(i));
        }
    }

    private static void alignRecycler(final RecyclerView rv) {
        RecyclerView.ItemAnimator ia = rv.getItemAnimator();
        if (ia != null) {
            ia.setAddDuration(MotionTokens.DUR_NORMAL);
            ia.setRemoveDuration(MotionTokens.DUR_FAST);
            ia.setMoveDuration(MotionTokens.DUR_NORMAL);
            ia.setChangeDuration(MotionTokens.DUR_FAST);
        }
        if (rv.getTag(R.id.tag_motion) != null) return;
        rv.setTag(R.id.tag_motion, Boolean.TRUE);
        final RecyclerView.Adapter<?> adapter = rv.getAdapter();
        if (adapter == null) return;
        if (adapter.getItemCount() > 0) {
            firstLoad(rv);
            return;
        }
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            private void check() {
                if (adapter.getItemCount() > 0) {
                    adapter.unregisterAdapterDataObserver(this);
                    firstLoad(rv);
                }
            }

            @Override
            public void onChanged() {
                check();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                check();
            }
        });
    }

    public static void playOnce(ViewGroup g) {
        if (g == null || g.getTag(R.id.tag_motion) != null || !animationsEnabled(g.getContext())) return;
        g.setTag(R.id.tag_motion, Boolean.TRUE);
        firstLoad(g);
    }

    private static void firstLoad(final ViewGroup rv) {
        LayoutAnimationController c = AnimationUtils.loadLayoutAnimation(rv.getContext(), R.anim.layout_fall_stagger);
        LayoutAnimationController capped = new LayoutAnimationController(c.getAnimation(), c.getDelay()) {
            @Override
            protected long getDelayForView(View view) {
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                int index = lp != null && lp.layoutAnimationParameters != null
                        ? lp.layoutAnimationParameters.index : 0;
                return Math.min(index, MotionTokens.MAX_STAGGERED - 1) * MotionTokens.STAGGER_STEP;
            }
        };
        capped.setOrder(LayoutAnimationController.ORDER_NORMAL);
        rv.setLayoutAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                rv.setLayoutAnimation(null);
                rv.setLayoutAnimationListener(null);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        rv.setLayoutAnimation(capped);
        rv.scheduleLayoutAnimation();
    }

    private static void popToolbar(ViewGroup tb) {
        java.util.ArrayList<View> icons = new java.util.ArrayList<>();
        collectIcons(tb, icons);
        for (View ic : icons) {
            ic.setAlpha(0f);
            ic.animate().alpha(1f).setDuration(MotionTokens.DUR_FAST)
                    .setInterpolator(MotionTokens.ENTER).start();
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
}
