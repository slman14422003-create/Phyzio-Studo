package com.ast2012a.clinicalmaster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class GoogleTtsClient {

    private GoogleTtsClient() {
    }

    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
    private static final String[] HOSTS = {"translate.google.com", "translate.googleapis.com"};
    private static final String[] CLIENTS = {"tw-ob", "gtx"};
    private static final int MAX_CHARS = 180;

    private static final Dispatcher DISPATCHER = new Dispatcher();

    static {
        DISPATCHER.setMaxRequests(16);
        DISPATCHER.setMaxRequestsPerHost(16);
    }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .dispatcher(DISPATCHER)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private static final ExecutorService POOL = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "gtts");
            t.setDaemon(true);
            return t;
        }
    });

    private static volatile long warmAt = 0L;
    private static volatile boolean warming = false;

    static void warmUp() {
        long now = System.currentTimeMillis();
        if (warming || now - warmAt < 240_000L) return;
        warming = true;
        Thread th = new Thread(() -> {
            try {
                Request r = new Request.Builder().url("https://translate.google.com/").head()
                        .header("User-Agent", USER_AGENT).build();
                try (Response resp = HTTP.newCall(r).execute()) {
                    warmAt = System.currentTimeMillis();
                }
            } catch (Throwable ignored) {
            } finally {
                warming = false;
            }
        }, "gtts-warm");
        th.setDaemon(true);
        th.start();
    }

    static String langOf(String voice) {
        if (voice == null) return "ar";
        String v = voice.toLowerCase();
        if (v.startsWith("g-")) v = v.substring(2);
        int d = v.indexOf('-');
        if (d > 0) v = v.substring(0, d);
        switch (v) {
            case "en":
            case "fr":
            case "tr":
            case "ar":
                return v;
            default:
                return "ar";
        }
    }

    static EdgeTtsClient.Result synthesize(String text, String voice, EdgeTtsClient.Style style) throws IOException {
        warmUp();
        return synth(text, langOf(voice));
    }

    static EdgeTtsClient.Result synthesizeRuns(String text, List<EdgeTtsClient.Run> runs, String baseVoice,
                                               EdgeTtsClient.Style style) throws IOException {
        if (runs == null || runs.size() < 2) return synthesize(text, baseVoice, style);
        warmUp();
        List<String> texts = new ArrayList<>();
        List<String> langs = new ArrayList<>();
        int pos = 0;
        String baseLang = langOf(baseVoice);
        for (EdgeTtsClient.Run r : runs) {
            int s = Math.max(pos, Math.min(text.length(), r.start));
            int e = Math.max(s, Math.min(text.length(), r.end));
            if (s > pos) {
                texts.add(text.substring(pos, s));
                langs.add(baseLang);
            }
            if (e > s) {
                texts.add(text.substring(s, e));
                langs.add(langOf(r.voice));
            }
            pos = e;
        }
        if (pos < text.length()) {
            texts.add(text.substring(pos));
            langs.add(baseLang);
        }
        List<String> mt = new ArrayList<>();
        List<String> ml = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            if (!mt.isEmpty() && ml.get(ml.size() - 1).equals(langs.get(i))) {
                mt.set(mt.size() - 1, mt.get(mt.size() - 1) + texts.get(i));
            } else {
                mt.add(texts.get(i));
                ml.add(langs.get(i));
            }
        }
        List<Future<EdgeTtsClient.Result>> fs = new ArrayList<>();
        for (int i = 0; i < mt.size(); i++) {
            final String t = mt.get(i);
            final String l = ml.get(i);
            fs.add(POOL.submit(() -> synth(t, l)));
        }
        return join(fs);
    }

    private static EdgeTtsClient.Result synth(String text, String lang) throws IOException {
        List<String> parts = split(text, lang);
        if (parts.isEmpty()) return new EdgeTtsClient.Result(new byte[0], new int[0], new int[0], 0);
        List<Future<EdgeTtsClient.Result>> fs = new ArrayList<>();
        for (String p : parts) {
            final String piece = p;
            fs.add(POOL.submit(() -> fetch(piece, lang)));
        }
        return join(fs);
    }

    private static EdgeTtsClient.Result join(List<Future<EdgeTtsClient.Result>> fs) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int dur = 0;
        IOException fail = null;
        for (Future<EdgeTtsClient.Result> f : fs) {
            try {
                EdgeTtsClient.Result r = f.get();
                if (r != null && r.audio != null) {
                    out.write(r.audio);
                    dur += r.durationMs;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                fail = new IOException("interrupted");
            } catch (ExecutionException ee) {
                Throwable c = ee.getCause();
                if (fail == null) {
                    fail = c instanceof IOException ? (IOException) c
                            : new IOException(c != null ? c.getMessage() : "tts failed", c);
                }
            }
        }
        if (fail != null) {
            for (Future<EdgeTtsClient.Result> f : fs) f.cancel(true);
            throw fail;
        }
        return new EdgeTtsClient.Result(out.toByteArray(), new int[0], new int[0], dur);
    }

    private static boolean speakable(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetterOrDigit(s.charAt(i))) return true;
        }
        return false;
    }

    private static String clean(String s, String lang) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("ar".equals(lang) && ArabicPhonetics.isMark(c)) continue;
            sb.append(c == '\n' || c == '\r' || c == '\t' ? ' ' : c);
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    static List<String> split(String text, String lang) {
        List<String> out = new ArrayList<>();
        String s = clean(text, lang);
        while (s.length() > MAX_CHARS) {
            int cut = -1;
            for (int i = MAX_CHARS; i > MAX_CHARS / 2; i--) {
                if (".!?\u061F\u061B;\u060C,:".indexOf(s.charAt(i - 1)) >= 0) {
                    cut = i;
                    break;
                }
            }
            if (cut < 0) {
                for (int i = MAX_CHARS; i > MAX_CHARS / 3; i--) {
                    if (s.charAt(i - 1) == ' ') {
                        cut = i;
                        break;
                    }
                }
            }
            if (cut < 0) cut = MAX_CHARS;
            String p = s.substring(0, cut).trim();
            if (speakable(p)) out.add(p);
            s = s.substring(cut).trim();
        }
        if (speakable(s)) out.add(s);
        return out;
    }

    private static EdgeTtsClient.Result fetch(String piece, String lang) throws IOException {
        IOException last = null;
        int tries = 0;
        for (int round = 0; round < 2; round++) {
            for (String host : HOSTS) {
                for (String client : CLIENTS) {
                    if (tries++ >= 6) break;
                    int code = 0;
                    try {
                        String url = "https://" + host + "/translate_tts?ie=UTF-8&client=" + client
                                + "&tl=" + lang + "&total=1&idx=0&textlen=" + piece.length()
                                + "&q=" + URLEncoder.encode(piece, "UTF-8");
                        Request req = new Request.Builder().url(url)
                                .header("User-Agent", USER_AGENT)
                                .header("Referer", "https://translate.google.com/")
                                .build();
                        try (Response resp = HTTP.newCall(req).execute()) {
                            code = resp.code();
                            ResponseBody body = resp.body();
                            if (!resp.isSuccessful() || body == null) {
                                throw new EdgeTtsClient.ServiceException("HTTP " + code, code);
                            }
                            byte[] data = body.bytes();
                            if (data.length < 200) throw new EdgeTtsClient.ServiceException("empty audio", code);
                            EdgeTtsClient.lastError = "";
                            return new EdgeTtsClient.Result(data, new int[0], new int[0], mp3DurationMs(data));
                        }
                    } catch (IOException e) {
                        last = e;
                        EdgeTtsClient.lastError = e.getMessage() == null ? "tts failed" : e.getMessage();
                        if (EdgeTtsClient.isNetworkFailure(e) && !(e.getMessage() != null
                                && e.getMessage().toLowerCase().contains("timeout"))) {
                            throw e;
                        }
                    }
                }
            }
            try {
                Thread.sleep(250L * (round + 1));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw last != null ? last : new IOException("tts failed");
    }

    private static final int[][] BITRATE = {
            {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},
            {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},
            {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},
            {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},
            {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160}
    };
    private static final int[][] SAMPLERATE = {{44100, 48000, 32000}, {22050, 24000, 16000}, {11025, 12000, 8000}};

    static int mp3DurationMs(byte[] d) {
        try {
            int i = 0;
            if (d.length > 10 && d[0] == 'I' && d[1] == 'D' && d[2] == '3') {
                i = 10 + (((d[6] & 0x7F) << 21) | ((d[7] & 0x7F) << 14) | ((d[8] & 0x7F) << 7) | (d[9] & 0x7F));
            }
            double samples = 0;
            int rate = 0;
            int frames = 0;
            while (i + 4 <= d.length) {
                if ((d[i] & 0xFF) != 0xFF || (d[i + 1] & 0xE0) != 0xE0) {
                    i++;
                    continue;
                }
                int ver = (d[i + 1] >> 3) & 3;
                int layer = (d[i + 1] >> 1) & 3;
                int br = (d[i + 2] >> 4) & 0xF;
                int sr = (d[i + 2] >> 2) & 3;
                int pad = (d[i + 2] >> 1) & 1;
                if (ver == 1 || layer != 1 || br == 0 || br == 15 || sr == 3) {
                    i++;
                    continue;
                }
                boolean v1 = ver == 3;
                int vi = v1 ? 0 : (ver == 2 ? 1 : 2);
                rate = SAMPLERATE[vi][sr];
                int bitrate = BITRATE[v1 ? 2 : 4][br] * 1000;
                int spf = v1 ? 1152 : 576;
                int len = (v1 ? 144 : 72) * bitrate / rate + pad;
                if (len < 4) {
                    i++;
                    continue;
                }
                samples += spf;
                frames++;
                i += len;
            }
            if (frames == 0 || rate == 0) return 0;
            return (int) (samples * 1000.0 / rate);
        } catch (Throwable t) {
            return 0;
        }
    }
}
