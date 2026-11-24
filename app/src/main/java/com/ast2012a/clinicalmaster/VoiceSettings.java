package com.ast2012a.clinicalmaster;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.util.Locale;

/** The read aloud block of the settings page. It rebuilds itself inside the given group after every change. */
final class VoiceSettings {
    private VoiceSettings() {
    }

    private interface Done {
        void ok(String v);
    }

    private interface Toggle {
        void on(boolean value);
    }

    static void render(final Activity a, final LinearLayout group) {
        group.removeAllViews();
        final Runnable rebuild = () -> render(a, group);
        final String eng = Voice.engine(a);
        final boolean cloud = !Voice.SYSTEM.equals(eng);

        row(a, group, R.drawable.ic_volume, a.getString(R.string.vs_engine), engineName(a, eng), true,
                v -> pickEngine(a, rebuild));
        if (cloud) note(a, group, R.string.vs_cloud_warn);
        if (Voice.EDGE.equals(eng)) note(a, group, R.string.vs_edge_note);

        if (cloud && !Voice.EDGE.equals(eng)) {
            final String keyName = eng + "_key";
            boolean has = !Voice.get(a, keyName, "").isEmpty();
            row(a, group, R.drawable.ic_key, a.getString(R.string.vs_key),
                    a.getString(has ? R.string.vs_key_saved : R.string.vs_key_missing), true,
                    v -> ask(a, R.string.vs_key, "", true, x -> {
                        if (!x.trim().isEmpty()) Voice.put(a, keyName, x);
                        rebuild.run();
                    }));
        }
        if (Voice.AZURE.equals(eng)) {
            row(a, group, R.drawable.ic_cloud, a.getString(R.string.vs_region),
                    Voice.get(a, "azure_region", "westeurope"), true,
                    v -> new ClaudeDialog(a).setTitle(a.getString(R.string.vs_region))
                            .setItems(Voice.AZURE_REGIONS, (d, i) -> {
                                Voice.put(a, "azure_region", Voice.AZURE_REGIONS[i]);
                                rebuild.run();
                            }).show());
        }
        if (cloud) {
            row(a, group, R.drawable.ic_volume, a.getString(R.string.vs_voice), voiceLabel(a, eng), true,
                    v -> pickVoice(a, eng, rebuild));
            row(a, group, R.drawable.ic_cloud, a.getString(R.string.vs_preload), preloadLabel(a), true,
                    v -> pickPreload(a, rebuild));
            if (!Voice.GEMINI.equals(eng)) {
                toggle(a, group, R.drawable.ic_sparkle, a.getString(R.string.vs_tashkeel),
                        a.getString(R.string.vs_tashkeel_sub), Voice.flag(a, "tashkeel"), on -> {
                            if (on && Voice.get(a, "gemini_key", "").isEmpty()) {
                                Toast.makeText(a, R.string.vs_tashkeel_need, Toast.LENGTH_LONG).show();
                                rebuild.run();
                                return;
                            }
                            Voice.putFlag(a, "tashkeel", on);
                            rebuild.run();
                        });
            }
            String proxy = Voice.get(a, "proxy", "");
            row(a, group, R.drawable.ic_globe, a.getString(R.string.vs_proxy),
                    proxy.isEmpty() ? a.getString(R.string.vs_proxy_none) : proxy, true,
                    v -> ask(a, R.string.vs_proxy, Voice.get(a, "proxy", ""), false, x -> {
                        Voice.put(a, "proxy", x);
                        rebuild.run();
                    }));
            row(a, group, R.drawable.ic_copy_out, a.getString(R.string.vs_worker), a.getString(R.string.vs_worker_sub),
                    false, v -> {
                        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("worker", Voice.WORKER_CODE));
                        Toast.makeText(a, R.string.vs_worker_copied, Toast.LENGTH_LONG).show();
                    });
        }
        row(a, group, R.drawable.ic_play, a.getString(R.string.vs_test), a.getString(R.string.vs_test_sub), false,
                v -> test(a));
        row(a, group, R.drawable.ic_info, a.getString(R.string.vs_steps), a.getString(R.string.vs_steps_sub), true,
                v -> steps(a, eng));
        row(a, group, R.drawable.ic_trash, a.getString(R.string.vs_cache),
                String.format(Locale.US, "%.1f MB", Voice.cacheSize(a) / 1048576f), false, v -> {
                    Voice.clearCache(a);
                    Toast.makeText(a, R.string.vs_cache_cleared, Toast.LENGTH_SHORT).show();
                    rebuild.run();
                });
    }

    // ------------------------------------------------------------------ row helpers

    private static void divider(Activity a, LinearLayout group) {
        if (group.getChildCount() == 0) return;
        View d = new View(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, 1));
        lp.setMarginStart(Ui.dp(a, 58));
        d.setLayoutParams(lp);
        d.setBackgroundResource(R.color.glass_border_soft);
        group.addView(d);
    }

    private static View inflateRow(Activity a, LinearLayout group, int icon, String title, String sub, boolean chevron) {
        divider(a, group);
        View r = LayoutInflater.from(a).inflate(R.layout.item_voice_row, group, false);
        ((ImageView) r.findViewById(R.id.vrow_icon)).setImageResource(icon);
        ((TextView) r.findViewById(R.id.vrow_title)).setText(title);
        TextView s = r.findViewById(R.id.vrow_sub);
        if (sub == null || sub.isEmpty()) s.setVisibility(View.GONE);
        else s.setText(sub);
        r.findViewById(R.id.vrow_chevron).setVisibility(chevron ? View.VISIBLE : View.GONE);
        group.addView(r);
        return r;
    }

    private static void row(Activity a, LinearLayout group, int icon, String title, String sub, boolean chevron,
                            View.OnClickListener click) {
        inflateRow(a, group, icon, title, sub, chevron).setOnClickListener(click);
    }

    private static void toggle(Activity a, LinearLayout group, int icon, String title, String sub, boolean on,
                               final Toggle cb) {
        final View r = inflateRow(a, group, icon, title, sub, false);
        final MaterialSwitch sw = r.findViewById(R.id.vrow_switch);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(on);
        r.setOnClickListener(v -> cb.on(!sw.isChecked()));
    }

    private static void note(Activity a, LinearLayout group, int text) {
        TextView t = (TextView) LayoutInflater.from(a).inflate(R.layout.item_voice_note, group, false);
        t.setText(text);
        group.addView(t);
    }

    // ------------------------------------------------------------------ labels and pickers

    private static String engineName(Activity a, String e) {
        switch (e) {
            case Voice.GEMINI:
                return a.getString(R.string.vs_e_gemini);
            case Voice.EDGE:
                return a.getString(R.string.vs_e_edge);
            case Voice.AZURE:
                return a.getString(R.string.vs_e_azure);
            case Voice.ELEVEN:
                return a.getString(R.string.vs_e_eleven);
            default:
                return a.getString(R.string.vs_e_system);
        }
    }

    private static final int[] PRELOAD = {0, 3, 6, 10};

    private static String preloadLabel(Activity a) {
        int n = Voice.preload(a);
        return n <= 0 ? a.getString(R.string.vs_preload_auto) : a.getString(R.string.vs_preload_n, n);
    }

    private static void pickPreload(final Activity a, final Runnable rebuild) {
        CharSequence[] items = new CharSequence[PRELOAD.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = PRELOAD[i] == 0 ? a.getString(R.string.vs_preload_auto) : a.getString(R.string.vs_preload_n, PRELOAD[i]);
        }
        new ClaudeDialog(a).setTitle(a.getString(R.string.vs_preload)).setItems(items, (d, i) -> {
            Voice.setPreload(a, PRELOAD[i]);
            rebuild.run();
        }).show();
    }

    private static String voiceLabel(Activity a, String eng) {
        String v = Voice.voice(a, eng);
        if (Voice.ELEVEN.equals(eng)) {
            for (String s : Voice.ELEVEN_VOICES) if (s.startsWith(v + "|")) return s.substring(s.indexOf('|') + 1);
        }
        return v;
    }

    private static void pickEngine(final Activity a, final Runnable rebuild) {
        CharSequence[] items = new CharSequence[Voice.ENGINES.length];
        for (int i = 0; i < items.length; i++) {
            String name = engineName(a, Voice.ENGINES[i]);
            items[i] = Voice.SYSTEM.equals(Voice.ENGINES[i]) ? name : name + "\n" + a.getString(R.string.vs_cloud_warn);
        }
        new ClaudeDialog(a).setTitle(a.getString(R.string.vs_engine)).setItems(items, (d, i) -> {
            Voice.setEngine(a, Voice.ENGINES[i]);
            rebuild.run();
            if (!Voice.SYSTEM.equals(Voice.ENGINES[i]) && !Voice.configured(a, Voice.ENGINES[i])) steps(a, Voice.ENGINES[i]);
        }).show();
    }

    private static void pickVoice(final Activity a, final String eng, final Runnable rebuild) {
        final String key = Voice.GEMINI.equals(eng) ? "gemini_voice" : Voice.AZURE.equals(eng) ? "azure_voice"
                : Voice.ELEVEN.equals(eng) ? "eleven_voice" : "edge_voice";
        if (Voice.ELEVEN.equals(eng)) {
            final String[] ids = new String[Voice.ELEVEN_VOICES.length + 1];
            CharSequence[] names = new CharSequence[ids.length];
            for (int i = 0; i < Voice.ELEVEN_VOICES.length; i++) {
                ids[i] = Voice.ELEVEN_VOICES[i].substring(0, Voice.ELEVEN_VOICES[i].indexOf('|'));
                names[i] = Voice.ELEVEN_VOICES[i].substring(Voice.ELEVEN_VOICES[i].indexOf('|') + 1);
            }
            names[ids.length - 1] = a.getString(R.string.vs_custom_id);
            new ClaudeDialog(a).setTitle(a.getString(R.string.vs_voice)).setItems(names, (d, i) -> {
                if (i == ids.length - 1) {
                    ask(a, R.string.vs_custom_id, "", false, x -> {
                        if (!x.trim().isEmpty()) Voice.put(a, key, x);
                        rebuild.run();
                    });
                } else {
                    Voice.put(a, key, ids[i]);
                    rebuild.run();
                }
            }).show();
            return;
        }
        final String[] list = Voice.GEMINI.equals(eng) ? Voice.GEMINI_VOICES : Voice.ARABIC_NEURAL;
        new ClaudeDialog(a).setTitle(a.getString(R.string.vs_voice)).setItems(list, (d, i) -> {
            Voice.put(a, key, list[i]);
            rebuild.run();
        }).show();
    }

    private static void ask(final Activity a, int title, String current, boolean secret, final Done cb) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 20);
        box.setPadding(p, Ui.dp(a, 8), p, 0);
        final EditText e = new EditText(a);
        e.setHint(title);
        e.setText(current);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_URI));
        e.setTextDirection(View.TEXT_DIRECTION_LTR);
        box.addView(e);
        new ClaudeDialog(a).setTitle(a.getString(title)).setView(box)
                .setPositiveButton(a.getString(android.R.string.ok), (d, w) -> cb.ok(e.getText().toString()))
                .setNegativeButton(a.getString(android.R.string.cancel), null).show();
    }

    // ------------------------------------------------------------------ test and steps

    private static void test(final Activity a) {
        final String eng = Voice.engine(a);
        if (!Voice.configured(a, eng)) {
            Toast.makeText(a, R.string.vs_key_missing, Toast.LENGTH_LONG).show();
            return;
        }
        final String sentence = a.getString(R.string.vs_test_sentence);
        if (Voice.SYSTEM.equals(eng)) {
            testSystem(a, sentence);
            return;
        }
        Toast.makeText(a, R.string.vs_testing, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                final File f = Voice.synth(a, sentence);
                a.runOnUiThread(() -> {
                    if (a.isFinishing() || a.isDestroyed()) return;
                    try {
                        final MediaPlayer mp = new MediaPlayer();
                        mp.setDataSource(f.getPath());
                        mp.prepare();
                        mp.setOnCompletionListener(MediaPlayer::release);
                        mp.start();
                        Toast.makeText(a, R.string.vs_test_ok, Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        f.delete();
                        Toast.makeText(a, String.valueOf(t.getMessage()), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (final Throwable t) {
                a.runOnUiThread(() -> Toast.makeText(a, String.valueOf(t.getMessage()), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static void testSystem(final Activity a, final String sentence) {
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(a.getApplicationContext(), st -> {
            TextToSpeech t = holder[0];
            if (st != TextToSpeech.SUCCESS || t == null) {
                a.runOnUiThread(() -> Toast.makeText(a, R.string.vs_tts_unavailable, Toast.LENGTH_LONG).show());
                if (t != null) t.shutdown();
                return;
            }
            int r = t.setLanguage(Locale.forLanguageTag("ar"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                a.runOnUiThread(() -> Toast.makeText(a, R.string.vs_tts_lang_missing, Toast.LENGTH_LONG).show());
                t.shutdown();
                return;
            }
            t.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override
                public void onStart(String id) {
                }

                @Override
                public void onDone(String id) {
                    a.runOnUiThread(() -> Toast.makeText(a, R.string.vs_test_ok, Toast.LENGTH_SHORT).show());
                    holder[0].shutdown();
                }

                @Override
                public void onError(String id) {
                    holder[0].shutdown();
                }
            });
            t.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "vs_test");
        });
    }

    private static void steps(Activity a, String eng) {
        int msg;
        switch (eng) {
            case Voice.GEMINI:
                msg = R.string.vs_steps_gemini;
                break;
            case Voice.EDGE:
                msg = R.string.vs_steps_edge;
                break;
            case Voice.AZURE:
                msg = R.string.vs_steps_azure;
                break;
            case Voice.ELEVEN:
                msg = R.string.vs_steps_eleven;
                break;
            default:
                msg = R.string.vs_steps_system;
                break;
        }
        new ClaudeDialog(a).setTitle(a.getString(R.string.vs_steps)).setMessage(a.getString(msg))
                .setPositiveButton(a.getString(android.R.string.ok), null).show();
    }
}
