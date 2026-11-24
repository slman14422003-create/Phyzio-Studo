package com.ast2012a.clinicalmaster;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/** Online reading voices (Gemini, Edge, Azure, ElevenLabs) with a per-paragraph file cache. */
final class Voice {
    private Voice() {
    }

    static final String SYSTEM = "system";
    static final String GEMINI = "gemini";
    static final String EDGE = "edge";
    static final String AZURE = "azure";
    static final String ELEVEN = "eleven";

    static final String[] ENGINES = {SYSTEM, GEMINI, EDGE, AZURE, ELEVEN};

    static final String[] GEMINI_VOICES = {"Kore", "Charon", "Puck", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr",
            "Algieba", "Sulafat", "Gacrux", "Schedar"};
    static final String[] ARABIC_NEURAL = {"ar-SY-AmanyNeural", "ar-SY-LaithNeural", "ar-SA-HamedNeural",
            "ar-SA-ZariyahNeural", "ar-EG-SalmaNeural", "ar-EG-ShakirNeural", "ar-JO-SanaNeural", "ar-JO-TaimNeural",
            "ar-AE-FatimaNeural", "ar-AE-HamdanNeural"};
    static final String[] ELEVEN_VOICES = {"21m00Tcm4TlvDq8ikWAM|Rachel", "pNInz6obpgDQGcFmaJgB|Adam",
            "EXAVITQu4vr4xnSDxMaL|Sarah", "ErXwobaYiN019PkySvjV|Antoni"};
    static final String[] AZURE_REGIONS = {"westeurope", "northeurope", "uaenorth", "eastus", "southeastasia"};

    static final String WORKER_CODE = "const ALLOW=[\"generativelanguage.googleapis.com\",\"api.elevenlabs.io\",\"speech.platform.bing.com\"];\n"
            + "const UA=\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0\";\n"
            + "export default {\n"
            + "  async fetch(req) {\n"
            + "    const u = new URL(req.url);\n"
            + "    const parts = u.pathname.split(\"/\").filter(Boolean);\n"
            + "    const host = parts.shift() || \"\";\n"
            + "    if (!(ALLOW.includes(host) || host.endsWith(\".tts.speech.microsoft.com\"))) return new Response(\"forbidden\", {status: 403});\n"
            + "    const target = \"https://\" + host + \"/\" + parts.join(\"/\") + u.search;\n"
            + "    const h = new Headers(req.headers);\n"
            + "    h.delete(\"host\");\n"
            + "    if (host === \"speech.platform.bing.com\") {\n"
            + "      h.set(\"Origin\", \"chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold\");\n"
            + "      h.set(\"User-Agent\", UA);\n"
            + "    }\n"
            + "    return fetch(target, {method: req.method, headers: h, body: (req.method === \"GET\" || req.method === \"HEAD\") ? undefined : req.body, redirect: \"follow\"});\n"
            + "  }\n"
            + "};\n";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("voice", Context.MODE_PRIVATE);
    }

    static String engine(Context c) {
        return sp(c).getString("engine", SYSTEM);
    }

    static void setEngine(Context c, String v) {
        sp(c).edit().putString("engine", v).apply();
    }

    private static boolean secret(String key) {
        return key.endsWith("_key");
    }

    static String get(Context c, String key, String def) {
        String raw = sp(c).getString(key, def);
        if (raw == null || !secret(key) || raw.isEmpty()) return raw;
        if (!raw.startsWith("v1:")) {
            String sealed = Vault.encrypt(raw);
            if (!sealed.isEmpty()) sp(c).edit().putString(key, sealed).apply();
            return raw;
        }
        String plain = Vault.decrypt(raw);
        return plain.isEmpty() ? def : plain;
    }

    static void put(Context c, String key, String v) {
        String val = v == null ? "" : v.trim();
        if (secret(key) && !val.isEmpty()) {
            String sealed = Vault.encrypt(val);
            if (!sealed.isEmpty()) val = sealed;
        }
        sp(c).edit().putString(key, val).apply();
    }

    static boolean flag(Context c, String key) {
        return sp(c).getBoolean(key, false);
    }

    static void putFlag(Context c, String key, boolean v) {
        sp(c).edit().putBoolean(key, v).apply();
    }

    static String voice(Context c, String engine) {
        switch (engine) {
            case GEMINI:
                return get(c, "gemini_voice", "Kore");
            case AZURE:
                return get(c, "azure_voice", "ar-SY-AmanyNeural");
            case ELEVEN:
                return get(c, "eleven_voice", "21m00Tcm4TlvDq8ikWAM");
            default:
                return get(c, "edge_voice", "ar-SY-AmanyNeural");
        }
    }

    static boolean configured(Context c, String engine) {
        switch (engine) {
            case GEMINI:
                return !get(c, "gemini_key", "").isEmpty();
            case AZURE:
                return !get(c, "azure_key", "").isEmpty();
            case ELEVEN:
                return !get(c, "eleven_key", "").isEmpty();
            default:
                return true;
        }
    }

    static int preload(Context c) {
        return sp(c).getInt("preload", 0);
    }

    static void setPreload(Context c, int n) {
        sp(c).edit().putInt("preload", n).apply();
    }

    /** How many pieces after the current one are generated ahead; 0 in the setting means automatic. */
    static int preloadAhead(Context c) {
        int n = preload(c);
        if (n > 0) return n;
        return GEMINI.equals(engine(c)) ? 3 : 6;
    }

    /** Characters per request: Gemini's free tier allows few requests, so it gets longer pieces. */
    static int chunkSize(Context c) {
        return GEMINI.equals(engine(c)) ? 280 : 420;
    }

    // ------------------------------------------------------------------ cache

    private static File dir(Context c) {
        File d = new File(c.getApplicationContext().getFilesDir(), "voice_cache");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static long cacheSize(Context c) {
        long n = 0;
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) n += f.length();
        return n;
    }

    private static final long CACHE_LIMIT = 300L * 1024 * 1024;

    private static void pruneCache(Context c, File keep) {
        File[] fs = dir(c).listFiles();
        if (fs == null) return;
        long total = 0;
        for (File f : fs) total += f.length();
        if (total <= CACHE_LIMIT) return;
        java.util.Arrays.sort(fs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : fs) {
            if (total <= CACHE_LIMIT * 3 / 4) break;
            if (f.equals(keep)) continue;
            total -= f.length();
            f.delete();
        }
    }

    static void clearCache(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    private static String sha(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    // ------------------------------------------------------------------ synthesis

    /** Returns an audio file for the text (from the cache when it was generated before). Blocking: call off the main thread. */
    static File synth(Context ctx, String text) throws IOException {
        Context c = ctx.getApplicationContext();
        String eng = engine(c);
        String voice = voice(c, eng);
        boolean tash = flag(c, "tashkeel") && !GEMINI.equals(eng);
        String ext = GEMINI.equals(eng) ? ".wav" : ".mp3";
        File out = new File(dir(c), sha(eng + "|" + voice + "|" + tash + "|" + text) + ext);
        if (out.exists() && out.length() > 0) return out;
        String say = tash ? diacritize(c, text) : text;
        byte[] audio;
        switch (eng) {
            case GEMINI:
                audio = gemini(c, say, voice);
                break;
            case AZURE:
                audio = azure(c, say, voice);
                break;
            case ELEVEN:
                audio = eleven(c, say, voice);
                break;
            default:
                audio = edge(c, say, voice);
                break;
        }
        if (audio == null || audio.length < 200) throw new IOException("empty audio");
        File tmp = new File(out.getPath() + ".part");
        try (OutputStream os = new FileOutputStream(tmp)) {
            os.write(audio);
        }
        if (!tmp.renameTo(out)) throw new IOException("cache write failed");
        pruneCache(c, out);
        return out;
    }

    private static String url(Context c, String host, String path) {
        String p = get(c, "proxy", "");
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p.isEmpty() ? "https://" + host + path : p + "/" + host + path;
    }

    private static byte[] post(String url, String[][] headers, byte[] body) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            try {
                h.setRequestMethod("POST");
                h.setConnectTimeout(20_000);
                h.setReadTimeout(90_000);
                h.setDoOutput(true);
                for (String[] kv : headers) h.setRequestProperty(kv[0], kv[1]);
                try (OutputStream os = h.getOutputStream()) {
                    os.write(body);
                }
                int code = h.getResponseCode();
                if (code >= 200 && code < 300) return readAll(h.getInputStream());
                String err = "";
                InputStream es = h.getErrorStream();
                if (es != null) err = new String(readAll(es), StandardCharsets.UTF_8);
                if (err.length() > 220) err = err.substring(0, 220);
                last = new IOException("HTTP " + code + " " + err);
                if (code != 429 && code != 503) throw last;
            } finally {
                h.disconnect();
            }
            try {
                Thread.sleep(10_000L * (attempt + 1));
            } catch (InterruptedException e) {
                throw new IOException("cancelled");
            }
        }
        throw last;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // Gemini ---------------------------------------------------------------

    private static final Object GEMINI_LOCK = new Object();
    private static long geminiLast;

    private static byte[] gemini(Context c, String text, String voice) throws IOException {
        synchronized (GEMINI_LOCK) {
            long wait = geminiLast + 3000L - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    throw new IOException("cancelled");
                }
            }
            try {
                return geminiCall(c, text, voice);
            } finally {
                geminiLast = System.currentTimeMillis();
            }
        }
    }

    private static byte[] geminiCall(Context c, String text, String voice) throws IOException {
        String key = get(c, "gemini_key", "");
        if (key.isEmpty()) throw new IOException("Gemini key missing");
        try {
            JSONObject body = new JSONObject()
                    .put("contents", new JSONArray().put(new JSONObject().put("parts",
                            new JSONArray().put(new JSONObject().put("text",
                                    c.getString(R.string.vs_prompt_read) + "\n" + text)))))
                    .put("generationConfig", new JSONObject()
                            .put("responseModalities", new JSONArray().put("AUDIO"))
                            .put("speechConfig", new JSONObject().put("voiceConfig", new JSONObject()
                                    .put("prebuiltVoiceConfig", new JSONObject().put("voiceName", voice)))));
            byte[] res = post(url(c, "generativelanguage.googleapis.com",
                            "/v1beta/models/gemini-2.5-flash-preview-tts:generateContent"),
                    new String[][]{{"Content-Type", "application/json"}, {"x-goog-api-key", key}},
                    body.toString().getBytes(StandardCharsets.UTF_8));
            JSONObject j = new JSONObject(new String(res, StandardCharsets.UTF_8));
            String b64 = j.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData").getString("data");
            return wav(Base64.decode(b64, Base64.DEFAULT), 24000);
        } catch (org.json.JSONException e) {
            throw new IOException("Gemini: bad response");
        }
    }

    private static byte[] wav(byte[] pcm, int rate) {
        int n = pcm.length;
        byte[] o = new byte[44 + n];
        put(o, 0, "RIFF");
        le32(o, 4, 36 + n);
        put(o, 8, "WAVEfmt ");
        le32(o, 16, 16);
        o[20] = 1;
        o[22] = 1;
        le32(o, 24, rate);
        le32(o, 28, rate * 2);
        o[32] = 2;
        o[34] = 16;
        put(o, 36, "data");
        le32(o, 40, n);
        System.arraycopy(pcm, 0, o, 44, n);
        return o;
    }

    private static void put(byte[] o, int at, String s) {
        for (int i = 0; i < s.length(); i++) o[at + i] = (byte) s.charAt(i);
    }

    private static void le32(byte[] o, int at, int v) {
        o[at] = (byte) v;
        o[at + 1] = (byte) (v >> 8);
        o[at + 2] = (byte) (v >> 16);
        o[at + 3] = (byte) (v >> 24);
    }

    /** Adds full diacritics with Gemini's text model (used for the engines that do not do it themselves). */
    private static String diacritize(Context c, String text) {
        String key = get(c, "gemini_key", "");
        if (key.isEmpty()) return text;
        File f = new File(dir(c), "t" + sha(text) + ".txt");
        try {
            if (f.exists()) return new String(readAll(new java.io.FileInputStream(f)), StandardCharsets.UTF_8);
            JSONObject body = new JSONObject().put("contents", new JSONArray().put(new JSONObject().put("parts",
                    new JSONArray().put(new JSONObject().put("text",
                            c.getString(R.string.vs_prompt_tashkeel) + "\n" + text)))));
            byte[] res = post(url(c, "generativelanguage.googleapis.com",
                            "/v1beta/models/gemini-2.5-flash:generateContent"),
                    new String[][]{{"Content-Type", "application/json"}, {"x-goog-api-key", key}},
                    body.toString().getBytes(StandardCharsets.UTF_8));
            String out = new JSONObject(new String(res, StandardCharsets.UTF_8)).getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim();
            if (out.length() < text.length() / 2) return text;
            try (OutputStream os = new FileOutputStream(f)) {
                os.write(out.getBytes(StandardCharsets.UTF_8));
            }
            return out;
        } catch (Throwable t) {
            return text;
        }
    }

    // Azure ----------------------------------------------------------------

    private static byte[] azure(Context c, String text, String voice) throws IOException {
        String key = get(c, "azure_key", "");
        String region = get(c, "azure_region", "westeurope");
        if (key.isEmpty()) throw new IOException("Azure key missing");
        String ssml = "<speak version='1.0' xml:lang='ar-SA'><voice name='" + voice + "'>" + esc(text) + "</voice></speak>";
        return post(url(c, region + ".tts.speech.microsoft.com", "/cognitiveservices/v1"),
                new String[][]{{"Ocp-Apim-Subscription-Key", key}, {"Content-Type", "application/ssml+xml"},
                        {"X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3"}, {"User-Agent", "ClinicalMaster"}},
                ssml.getBytes(StandardCharsets.UTF_8));
    }

    // ElevenLabs -----------------------------------------------------------

    private static byte[] eleven(Context c, String text, String voice) throws IOException {
        String key = get(c, "eleven_key", "");
        if (key.isEmpty()) throw new IOException("ElevenLabs key missing");
        try {
            JSONObject body = new JSONObject().put("text", text).put("model_id", "eleven_multilingual_v2");
            return post(url(c, "api.elevenlabs.io", "/v1/text-to-speech/" + voice + "?output_format=mp3_44100_64"),
                    new String[][]{{"xi-api-key", key}, {"Content-Type", "application/json"}, {"Accept", "audio/mpeg"}},
                    body.toString().getBytes(StandardCharsets.UTF_8));
        } catch (org.json.JSONException e) {
            throw new IOException("ElevenLabs: bad request");
        }
    }

    // Edge neural ----------------------------------------------------------

    private static final String EDGE_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String EDGE_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";
    private static volatile long edgeSkew;
    private static final java.util.Random RND = new java.util.Random();

    private static String gec() {
        return EdgeToken.token(EDGE_TOKEN, System.currentTimeMillis() / 1000L + edgeSkew);
    }

    private static String muid() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) sb.append("0123456789ABCDEF".charAt(RND.nextInt(16)));
        return sb.toString();
    }

    private static byte[] edge(Context c, String text, String voice) throws IOException {
        try {
            return edgeOnce(c, text, voice);
        } catch (EdgeSkew e) {
            return edgeOnce(c, text, voice);
        }
    }

    /** Raised when the server refused with 403 and told its own clock, so one retry with the corrected clock is worthwhile. */
    private static final class EdgeSkew extends IOException {
        EdgeSkew(String m) {
            super(m);
        }
    }

    private static byte[] edgeOnce(Context c, String text, String voice) throws IOException {
        final String id = UUID.randomUUID().toString().replace("-", "");
        String u = url(c, "speech.platform.bing.com", "/consumer/speech/synthesize/readaloud/edge/v1")
                + "?TrustedClientToken=" + EDGE_TOKEN + "&Sec-MS-GEC=" + gec() + "&Sec-MS-GEC-Version=" + EdgeToken.VERSION
                + "&ConnectionId=" + id;
        u = u.replaceFirst("^https://", "wss://");
        final String lang = voice.length() >= 5 ? voice.substring(0, 5) : "ar-SA";
        final String stamp = new java.text.SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
                Locale.US).format(new java.util.Date());
        final String cfg = "X-Timestamp:" + stamp + "\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n"
                + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\","
                + "\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n";
        final String ssml = "X-RequestId:" + id + "\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:" + stamp
                + "Z\r\nPath:ssml\r\n\r\n<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='" + lang
                + "'><voice name='" + voice + "'><prosody pitch='+0Hz' rate='+0%' volume='+0%'>" + esc(text)
                + "</prosody></voice></speak>";

        final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        final CountDownLatch done = new CountDownLatch(1);
        final String[] error = {null};
        final boolean[] retry = {false};
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS).build();
        Request req = new Request.Builder().url(u)
                .header("Pragma", "no-cache").header("Cache-Control", "no-cache")
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("User-Agent", EDGE_UA).header("Cookie", "muid=" + muid() + ";").build();
        WebSocket ws = client.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket w, Response r) {
                w.send(cfg);
                w.send(ssml);
            }

            @Override
            public void onMessage(WebSocket w, String t) {
                if (t.contains("Path:turn.end")) {
                    done.countDown();
                    w.close(1000, null);
                }
            }

            @Override
            public void onMessage(WebSocket w, ByteString b) {
                byte[] d = b.toByteArray();
                if (d.length < 2) return;
                int hl = ((d[0] & 0xFF) << 8) | (d[1] & 0xFF);
                if (hl + 2 > d.length) return;
                String head = new String(d, 2, hl, StandardCharsets.UTF_8);
                if (head.contains("Path:audio")) audio.write(d, 2 + hl, d.length - 2 - hl);
            }

            @Override
            public void onFailure(WebSocket w, Throwable t, Response r) {
                error[0] = (r != null ? "HTTP " + r.code() + " " : "") + (t == null ? "" : String.valueOf(t.getMessage()));
                if (r != null && r.code() == 403) {
                    java.util.Date d = r.headers().getDate("Date");
                    if (d != null) {
                        long skew = d.getTime() / 1000L - System.currentTimeMillis() / 1000L;
                        if (Math.abs(skew - edgeSkew) > 20) {
                            edgeSkew = skew;
                            retry[0] = true;
                        }
                    }
                }
                done.countDown();
            }

            @Override
            public void onClosed(WebSocket w, int code, String reason) {
                done.countDown();
            }
        });
        try {
            if (!done.await(60, TimeUnit.SECONDS)) {
                ws.cancel();
                throw new IOException("Edge: timeout");
            }
        } catch (InterruptedException e) {
            ws.cancel();
            throw new IOException("cancelled");
        } finally {
            client.dispatcher().executorService().shutdown();
        }
        if (error[0] != null) {
            if (retry[0]) throw new EdgeSkew(error[0]);
            throw new IOException("Edge: " + error[0]);
        }
        return audio.toByteArray();
    }
}
