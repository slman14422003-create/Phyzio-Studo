package com.ast2012a.clinicalmaster;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read aloud for the PDF reader. Page text comes from PdfTextSource, is split into sentence pieces with offsets, and is
 * spoken either by the system TextToSpeech or by VoicePlayer for the cloud engines. The piece being read is highlighted
 * on the page through the host.
 */
final class PdfReadAloud {

    interface Host {
        File file();

        int visiblePage();

        int pageCount();

        void showReading(int page, List<RectF> rects);

        void clearReading();
    }

    static final float RATE_MIN = 0.7f;
    static final float RATE_STEP = 0.1f;
    static final int RATE_STEPS = 8;
    private static final String PREF_RATE = "read_rate";
    private static final int MAX_EMPTY_SKIP = 6;

    private final Activity act;
    private final Host host;
    private final SharedPreferences prefs;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final View bar;
    private final ImageButton btnPlay;
    private final ImageButton btnPrev;
    private final ImageButton btnNext;
    private final ImageButton btnStop;
    private final TextView status;
    private final TextView speedLabel;
    private final SeekBar speedBar;

    private ExecutorService ex;
    private PdfTextSource.Source source;
    private File sourceFile;

    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean ttsIniting;
    private Runnable afterTtsReady;
    private VoicePlayer player;

    private boolean active;
    private boolean spoke;
    private boolean paused;
    private boolean systemMode;
    private int gen;
    private int page;
    private PdfTextSource.PageText pt;
    private VoiceChunker chunks;
    private int cur;
    private int queuedUpTo;
    private float rate;

    PdfReadAloud(Activity act, Host host, SharedPreferences prefs) {
        this.act = act;
        this.host = host;
        this.prefs = prefs;
        bar = act.findViewById(R.id.voice_bar);
        btnPlay = act.findViewById(R.id.voice_play);
        btnPrev = act.findViewById(R.id.voice_prev);
        btnNext = act.findViewById(R.id.voice_next);
        btnStop = act.findViewById(R.id.voice_stop);
        status = act.findViewById(R.id.voice_status);
        speedLabel = act.findViewById(R.id.voice_speed_label);
        speedBar = act.findViewById(R.id.voice_speed);
        rate = clampRate(prefs.getFloat(PREF_RATE, 1f));
        bindUi();
    }

    private static float clampRate(float r) {
        int step = Math.round((r - RATE_MIN) / RATE_STEP);
        step = Math.max(0, Math.min(RATE_STEPS, step));
        return Math.round((RATE_MIN + step * RATE_STEP) * 10f) / 10f;
    }

    private void bindUi() {
        speedBar.setMax(RATE_STEPS);
        speedBar.setProgress(Math.round((rate - RATE_MIN) / RATE_STEP));
        showRate();
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                rate = clampRate(RATE_MIN + p * RATE_STEP);
                showRate();
                if (player != null) player.setRate(rate);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                prefs.edit().putFloat(PREF_RATE, rate).apply();
                if (active && systemMode && !paused) speakFrom(cur);
            }
        });
        btnPlay.setOnClickListener(v -> togglePause());
        btnStop.setOnClickListener(v -> stop());
        btnNext.setOnClickListener(v -> stepParagraph(1));
        btnPrev.setOnClickListener(v -> stepParagraph(-1));
    }

    private void showRate() {
        speedLabel.setText(act.getString(R.string.vs_speed_fmt, rate));
    }

    boolean isActive() {
        return active;
    }

    // ------------------------------------------------------------------ session

    /** Starts reading from the page currently on screen. */
    void startFromVisiblePage() {
        File f = host.file();
        if (f == null || !f.exists()) return;
        String eng = Voice.engine(act);
        if (!Voice.SYSTEM.equals(eng) && !Voice.configured(act, eng)) {
            Toast.makeText(act, R.string.vs_key_needed, Toast.LENGTH_LONG).show();
            return;
        }
        stop();
        active = true;
        spoke = false;
        paused = false;
        systemMode = Voice.SYSTEM.equals(eng);
        int g = ++gen;
        showBar(true);
        setStatus(act.getString(R.string.vs_loading));
        updatePlayIcon();
        loadPage(host.visiblePage(), true, 0, g, 0);
    }

    /** Loads a page off the main thread, then starts reading it at the given piece (or the paragraph start when -1). */
    private void loadPage(final int p, final boolean forward, final int fromPiece, final int g, final int skipped) {
        final File f = host.file();
        if (f == null) {
            stop();
            return;
        }
        executor().execute(() -> {
            PdfTextSource.PageText loaded = null;
            try {
                if (source == null || !f.equals(sourceFile)) {
                    closeSourceNow();
                    source = PdfTextSource.Source.open(act.getApplicationContext(), f);
                    sourceFile = f;
                }
                if (p < 0 || p >= source.pageCount()) {
                    h.post(() -> {
                        if (g != gen) return;
                        finishReading();
                    });
                    return;
                }
                loaded = source.page(p);
            } catch (Throwable t) {
                final String m = String.valueOf(t.getMessage());
                h.post(() -> {
                    if (g != gen) return;
                    fail(m);
                });
                return;
            }
            final PdfTextSource.PageText result = loaded;
            h.post(() -> {
                if (g != gen || !active) return;
                onPageLoaded(p, result, forward, fromPiece, skipped);
            });
        });
    }

    private void onPageLoaded(int p, PdfTextSource.PageText loaded, boolean forward, int fromPiece, int skipped) {
        String text = loaded == null ? "" : loaded.text;
        VoiceChunker c = VoiceChunker.split(text, Voice.chunkSize(act));
        if (c.count() == 0) {
            int step = forward ? 1 : -1;
            int nextPage = p + step;
            if (skipped >= MAX_EMPTY_SKIP || nextPage < 0 || nextPage >= host.pageCount()) {
                if (!spoke) {
                    Toast.makeText(act, R.string.vs_no_text, Toast.LENGTH_LONG).show();
                    stop();
                } else {
                    finishReading();
                }
                return;
            }
            loadPage(nextPage, forward, 0, gen, skipped + 1);
            return;
        }
        spoke = true;
        page = p;
        pt = loaded;
        chunks = c;
        cur = Math.max(0, Math.min(fromPiece, c.count() - 1));
        if (!forward && fromPiece == 0) cur = 0;
        beginPlayback(cur);
    }

    private void beginPlayback(int from) {
        final int g = gen;
        setStatus(act.getString(R.string.vs_loading));
        if (systemMode) {
            ensureTts(() -> {
                if (g != gen || !active) return;
                speakFrom(from);
            });
        } else {
            startCloud(from);
        }
    }

    // ------------------------------------------------------------------ cloud engines

    private void startCloud(int from) {
        if (player == null) {
            player = new VoicePlayer(act, new VoicePlayer.Cb() {
                @Override
                public void onChunk(int index) {
                    if (!active || systemMode) return;
                    cur = index;
                    onPieceStarted(index);
                }

                @Override
                public void onDone() {
                    if (!active || systemMode) return;
                    pageFinished();
                }

                @Override
                public void onError(String message) {
                    if (!active || systemMode) return;
                    fail(message);
                }
            });
        }
        player.start(pt.text, chunks.starts, chunks.ends, from, rate);
        if (paused) player.pause();
    }

    // ------------------------------------------------------------------ system engine

    private void ensureTts(final Runnable ready) {
        if (tts != null && ttsReady) {
            ready.run();
            return;
        }
        afterTtsReady = ready;
        if (ttsIniting) return;
        ttsIniting = true;
        final Context app = act.getApplicationContext();
        tts = new TextToSpeech(app, status0 -> h.post(() -> {
            ttsIniting = false;
            if (status0 != TextToSpeech.SUCCESS || tts == null) {
                tts = null;
                ttsReady = false;
                afterTtsReady = null;
                if (active) fail(act.getString(R.string.vs_tts_unavailable));
                return;
            }
            ttsReady = true;
            tts.setOnUtteranceProgressListener(progress);
            Runnable r = afterTtsReady;
            afterTtsReady = null;
            if (r != null) r.run();
        }));
    }

    private final UtteranceProgressListener progress = new UtteranceProgressListener() {
        @Override
        public void onStart(String id) {
            final int[] k = parseId(id);
            if (k == null) return;
            h.post(() -> {
                if (k[0] != gen || !active || !systemMode) return;
                cur = k[1];
                onPieceStarted(k[1]);
                queueAhead();
            });
        }

        @Override
        public void onDone(String id) {
            final int[] k = parseId(id);
            if (k == null) return;
            h.post(() -> {
                if (k[0] != gen || !active || !systemMode) return;
                if (chunks != null && k[1] >= chunks.count() - 1) pageFinished();
            });
        }

        @Override
        public void onError(String id) {
            final int[] k = parseId(id);
            if (k == null) return;
            h.post(() -> {
                if (k[0] != gen || !active || !systemMode) return;
                fail(act.getString(R.string.vs_tts_unavailable));
            });
        }
    };

    private static int[] parseId(String id) {
        if (id == null) return null;
        int i = id.indexOf(':');
        if (i <= 0) return null;
        try {
            return new int[]{Integer.parseInt(id.substring(0, i)), Integer.parseInt(id.substring(i + 1))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean applyLanguage() {
        if (tts == null) return false;
        Locale want = VoiceChunker.arabicDominant(pt.text) ? Locale.forLanguageTag("ar") : Locale.getDefault();
        int r = tts.setLanguage(want);
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    /** Flushes whatever is queued and speaks from the given piece; later pieces are queued as each one starts. */
    private void speakFrom(int from) {
        if (tts == null || !ttsReady || chunks == null || pt == null) return;
        gen++;
        if (!applyLanguage()) {
            Toast.makeText(act, R.string.vs_tts_lang_missing, Toast.LENGTH_LONG).show();
            stop();
            return;
        }
        tts.setSpeechRate(rate);
        cur = from;
        queuedUpTo = from - 1;
        tts.stop();
        enqueue(from, TextToSpeech.QUEUE_FLUSH);
        queueAhead();
    }

    private void queueAhead() {
        if (chunks == null || tts == null) return;
        int last = Math.min(chunks.count() - 1, cur + 2);
        while (queuedUpTo < last) enqueue(queuedUpTo + 1, TextToSpeech.QUEUE_ADD);
    }

    private void enqueue(int i, int mode) {
        if (i < 0 || i >= chunks.count()) return;
        String piece = pt.text.substring(chunks.starts[i], chunks.ends[i]);
        tts.speak(piece, mode, null, gen + ":" + i);
        queuedUpTo = i;
    }

    // ------------------------------------------------------------------ progress and highlight

    private void onPieceStarted(int index) {
        setStatus(act.getString(R.string.vs_read_aloud) + "  " + (page + 1) + "/" + host.pageCount());
        List<RectF> rects = new ArrayList<>();
        if (pt != null && chunks != null && index >= 0 && index < chunks.count()) {
            int s = chunks.starts[index];
            int e = chunks.ends[index];
            for (PdfTextSource.Word w : pt.words) {
                if (w.start < e && w.end > s && w.box != null) rects.add(new RectF(w.box));
            }
        }
        host.showReading(page, rects);
    }

    private void pageFinished() {
        int next = page + 1;
        if (next >= host.pageCount()) {
            finishReading();
            return;
        }
        halt();
        host.clearReading();
        int g = ++gen;
        loadPage(next, true, 0, g, 0);
    }

    private void finishReading() {
        Toast.makeText(act, R.string.vs_end, Toast.LENGTH_SHORT).show();
        stop();
    }

    private void fail(String message) {
        String m = message == null || message.isEmpty() || "null".equals(message) ? act.getString(R.string.vs_tts_unavailable) : message;
        Toast.makeText(act, m, Toast.LENGTH_LONG).show();
        stop();
    }

    // ------------------------------------------------------------------ controls

    private void togglePause() {
        if (!active || chunks == null) return;
        if (paused) {
            paused = false;
            if (systemMode) speakFrom(cur);
            else if (player != null) player.resume();
        } else {
            paused = true;
            if (systemMode) {
                gen++;
                if (tts != null) tts.stop();
            } else if (player != null) {
                player.pause();
            }
        }
        updatePlayIcon();
    }

    private void stepParagraph(int dir) {
        if (!active || chunks == null || pt == null) return;
        int target = chunks.paragraphStep(pt.text, cur, dir);
        if (target >= chunks.count()) {
            int next = page + 1;
            if (next >= host.pageCount()) {
                finishReading();
                return;
            }
            halt();
            host.clearReading();
            loadPage(next, true, 0, ++gen, 0);
            return;
        }
        if (dir < 0 && target == 0 && cur == 0 && page > 0) {
            halt();
            host.clearReading();
            loadPage(page - 1, false, 0, ++gen, 0);
            return;
        }
        paused = false;
        updatePlayIcon();
        if (systemMode) {
            speakFrom(target);
        } else {
            startCloud(target);
        }
    }

    /** Called by the reader when the user jumps to another page, which ends the reading. */
    void onUserJump() {
        if (active) stop();
    }

    /** Stops every audio source but keeps the session objects so the next start is quick. */
    private void halt() {
        gen++;
        if (tts != null) {
            try {
                tts.stop();
            } catch (Throwable ignored) {
            }
        }
        if (player != null) player.stop();
    }

    void stop() {
        boolean was = active;
        active = false;
        paused = false;
        halt();
        pt = null;
        chunks = null;
        queuedUpTo = -1;
        afterTtsReady = null;
        host.clearReading();
        showBar(false);
        if (was) closeSourceAsync();
    }

    /** Stops reading and frees the voice engines, the page source and the worker thread. */
    void release() {
        stop();
        if (player != null) {
            player.release();
            player = null;
        }
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Throwable ignored) {
            }
            tts = null;
        }
        ttsReady = false;
        ttsIniting = false;
        if (ex != null) {
            ex.execute(this::closeSourceNow);
            ex.shutdown();
            ex = null;
        }
        h.removeCallbacksAndMessages(null);
    }

    // ------------------------------------------------------------------ helpers

    private ExecutorService executor() {
        if (ex == null) ex = Executors.newSingleThreadExecutor();
        return ex;
    }

    private void closeSourceNow() {
        if (source != null) {
            source.close();
            source = null;
            sourceFile = null;
        }
    }

    private void closeSourceAsync() {
        if (ex == null) return;
        ex.execute(this::closeSourceNow);
    }

    private void showBar(boolean show) {
        bar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private void updatePlayIcon() {
        btnPlay.setImageResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);
        btnPlay.setContentDescription(act.getString(paused ? R.string.vs_play : R.string.vs_pause));
    }
}
