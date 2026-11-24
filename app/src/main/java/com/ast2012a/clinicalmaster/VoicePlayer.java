package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Plays the chapter piece by piece. A window of the next pieces is generated ahead of the one being read, the window
 * is refilled every time a piece finishes, and the next piece is already prepared so the switch has no wait.
 */
final class VoicePlayer {
    interface Cb {
        void onChunk(int index);

        void onDone();

        void onError(String message);
    }

    private final Context app;
    private final Cb cb;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final ExecutorService waiter = Executors.newSingleThreadExecutor();
    private final Map<Integer, Future<File>> jobs = new HashMap<>();
    private MediaPlayer mp;
    private MediaPlayer next;
    private File nextFile;
    private int nextFor = -1;
    private boolean nextReady;
    private int gen;
    private String text = "";
    private int[] s = new int[0];
    private int[] e = new int[0];
    private int cur;
    private float rate = 1f;
    private boolean paused;

    VoicePlayer(Context c, Cb cb) {
        this.app = c.getApplicationContext();
        this.cb = cb;
    }

    void start(String text, int[] starts, int[] ends, int from, float rate) {
        stop();
        this.text = text;
        this.s = starts;
        this.e = ends;
        this.cur = Math.max(0, from);
        this.rate = rate;
        this.paused = false;
        play(gen);
    }

    /** Generates one piece; a short transient failure gets a single retry before it reaches the player. */
    private File synthWithRetry(String piece) throws IOException {
        try {
            return Voice.synth(app, piece);
        } catch (IOException first) {
            if (Thread.currentThread().isInterrupted()) throw first;
            try {
                Thread.sleep(1200);
            } catch (InterruptedException ie) {
                throw new IOException("cancelled");
            }
            return Voice.synth(app, piece);
        }
    }

    /** Keeps the window [cur, cur + ahead) generated or in progress. Main thread only. */
    private void fill(final int g) {
        if (g != gen) return;
        int ahead = Voice.preloadAhead(app);
        for (int k = cur; k <= cur + ahead && k < s.length; k++) {
            if (jobs.containsKey(k)) continue;
            final String piece = text.substring(s[k], e[k]);
            jobs.put(k, pool.submit(() -> {
                try {
                    return synthWithRetry(piece);
                } finally {
                    h.post(() -> {
                        if (g != gen) return;
                        fill(g);
                        prepareNext(g);
                    });
                }
            }));
        }
    }

    /** Prepares the player of the following piece as soon as its audio exists. Main thread only. */
    private void prepareNext(int g) {
        if (g != gen || next != null || mp == null) return;
        int idx = cur + 1;
        if (idx >= s.length) return;
        Future<File> f = jobs.get(idx);
        if (f == null || !f.isDone() || f.isCancelled()) return;
        File file;
        try {
            file = f.get();
        } catch (Throwable t) {
            return;
        }
        MediaPlayer p = null;
        try {
            p = new MediaPlayer();
            p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            p.setDataSource(file.getPath());
            final MediaPlayer pp = p;
            p.setOnPreparedListener(m -> {
                if (next == pp) nextReady = true;
            });
            p.setOnErrorListener((m, what, extra) -> {
                if (next == pp) releaseNext();
                return true;
            });
            next = p;
            nextFile = file;
            nextFor = idx;
            nextReady = false;
            p.prepareAsync();
        } catch (Throwable t) {
            if (p != null) {
                try {
                    p.release();
                } catch (Throwable ignored) {
                }
            }
            next = null;
            nextFile = null;
            nextFor = -1;
            nextReady = false;
        }
    }

    private void releaseNext() {
        if (next != null) {
            try {
                next.setOnPreparedListener(null);
                next.setOnErrorListener(null);
                next.release();
            } catch (Throwable ignored) {
            }
        }
        next = null;
        nextFile = null;
        nextFor = -1;
        nextReady = false;
    }

    private void play(final int g) {
        if (g != gen) return;
        if (cur >= s.length) {
            cb.onDone();
            return;
        }
        if (next != null && nextFor == cur && nextReady) {
            MediaPlayer p = next;
            File file = nextFile;
            next = null;
            nextFile = null;
            nextFor = -1;
            nextReady = false;
            attach(g, cur, p, file);
            return;
        }
        releaseNext();
        fill(g);
        final int idx = cur;
        final Future<File> f = jobs.get(idx);
        waiter.submit(() -> {
            try {
                final File file = f.get();
                h.post(() -> begin(g, idx, file));
            } catch (final Throwable t) {
                Throwable root = t.getCause() != null ? t.getCause() : t;
                final String m = String.valueOf(root.getMessage());
                h.post(() -> {
                    if (g == gen) cb.onError(m);
                });
            }
        });
    }

    private void begin(final int g, int idx, File file) {
        if (g != gen) return;
        try {
            MediaPlayer p = new MediaPlayer();
            p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            p.setDataSource(file.getPath());
            p.prepare();
            attach(g, idx, p, file);
        } catch (Throwable t) {
            file.delete();
            cb.onError(String.valueOf(t.getMessage()));
        }
    }

    /** Makes a prepared player the current one and starts it unless paused. */
    private void attach(final int g, int idx, MediaPlayer p, final File file) {
        if (g != gen) {
            try {
                p.release();
            } catch (Throwable ignored) {
            }
            return;
        }
        releasePlayer();
        p.setOnPreparedListener(null);
        p.setOnCompletionListener(m -> {
            if (g != gen) return;
            cur++;
            h.post(() -> play(g));
        });
        p.setOnErrorListener((m, what, extra) -> {
            if (g == gen) {
                file.delete();
                cb.onError("audio error " + what);
            }
            return true;
        });
        mp = p;
        cb.onChunk(idx);
        if (!paused) {
            try {
                p.start();
                applyRate();
            } catch (Throwable t) {
                file.delete();
                cb.onError(String.valueOf(t.getMessage()));
                return;
            }
        }
        fill(g);
        prepareNext(g);
    }

    private void applyRate() {
        try {
            if (mp != null) mp.setPlaybackParams(new PlaybackParams().setSpeed(rate));
        } catch (Throwable ignored) {
        }
    }

    void setRate(float r) {
        rate = r;
        if (mp != null && !paused) applyRate();
    }

    void pause() {
        paused = true;
        try {
            if (mp != null && mp.isPlaying()) mp.pause();
        } catch (Throwable ignored) {
        }
    }

    void resume() {
        paused = false;
        try {
            if (mp != null && !mp.isPlaying()) {
                mp.start();
                applyRate();
            }
        } catch (Throwable ignored) {
        }
    }

    int index() {
        return cur;
    }

    float progress() {
        try {
            if (mp != null) {
                int d = mp.getDuration();
                if (d > 0) return Math.max(0f, Math.min(1f, mp.getCurrentPosition() / (float) d));
            }
        } catch (Throwable ignored) {
        }
        return 0f;
    }

    boolean isPaused() {
        return paused;
    }

    void stop() {
        gen++;
        for (Future<File> f : jobs.values()) f.cancel(true);
        jobs.clear();
        releaseNext();
        releasePlayer();
    }

    private void releasePlayer() {
        if (mp == null) return;
        try {
            mp.setOnCompletionListener(null);
            mp.release();
        } catch (Throwable ignored) {
        }
        mp = null;
    }

    void release() {
        stop();
        pool.shutdownNow();
        waiter.shutdownNow();
    }
}
