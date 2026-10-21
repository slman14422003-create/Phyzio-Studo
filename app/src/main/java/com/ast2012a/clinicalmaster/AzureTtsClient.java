package com.ast2012a.clinicalmaster;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class AzureTtsClient {

    private AzureTtsClient() {
    }

    static final int MAX_CHARS = 2500;

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    static EdgeTtsClient.Result synthesize(String text, List<EdgeTtsClient.Run> runs, String voice,
                                           EdgeTtsClient.Style style, String key, String region) throws IOException {
        if (text.length() <= MAX_CHARS) {
            return post(ssml(text, runs, voice, style), key, region);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int dur = 0;
        for (String piece : split(text)) {
            EdgeTtsClient.Result r = post(ssml(piece, null, voice, style), key, region);
            out.write(r.audio);
            dur += r.durationMs;
        }
        return new EdgeTtsClient.Result(out.toByteArray(), new int[0], new int[0], dur);
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        while (s.length() > MAX_CHARS) {
            int cut = -1;
            for (int i = MAX_CHARS; i > MAX_CHARS / 2; i--) {
                if (".!?\u061F\u061B;\u060C,:".indexOf(s.charAt(i - 1)) >= 0) {
                    cut = i;
                    break;
                }
            }
            if (cut < 0) cut = s.lastIndexOf(' ', MAX_CHARS) > 0 ? s.lastIndexOf(' ', MAX_CHARS) : MAX_CHARS;
            out.add(s.substring(0, cut));
            s = s.substring(cut);
        }
        if (!s.trim().isEmpty()) out.add(s);
        return out;
    }

    private static String localeOf(String voice) {
        String[] p = voice.split("-");
        return p.length >= 2 ? p[0] + "-" + p[1] : "ar-SA";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String signed(int v) {
        return (v >= 0 ? "+" : "") + v;
    }

    private static String voiceEl(String voice, String piece, EdgeTtsClient.Style st) {
        boolean ar = voice.startsWith("ar-");
        int rate = ar ? st.arRatePct : 0;
        String body = esc(piece);
        if (rate != 0 || st.pitchHz != 0) {
            body = "<prosody rate='" + signed(rate) + "%' pitch='" + signed(st.pitchHz) + "Hz'>" + body + "</prosody>";
        }
        return "<voice name='" + voice + "'>" + body + "</voice>";
    }

    private static String ssml(String text, List<EdgeTtsClient.Run> runs, String voice, EdgeTtsClient.Style st) {
        StringBuilder sb = new StringBuilder();
        sb.append("<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='")
                .append(localeOf(voice)).append("'>");
        if (runs == null || runs.size() < 2) {
            sb.append(voiceEl(voice, text, st));
        } else {
            int pos = 0;
            for (EdgeTtsClient.Run r : runs) {
                int s = Math.max(pos, Math.min(text.length(), r.start));
                int e = Math.max(s, Math.min(text.length(), r.end));
                if (s > pos) sb.append(voiceEl(voice, text.substring(pos, s), st));
                if (e > s) sb.append(voiceEl(r.voice, text.substring(s, e), st));
                pos = e;
            }
            if (pos < text.length()) sb.append(voiceEl(voice, text.substring(pos), st));
        }
        return sb.append("</speak>").toString();
    }

    private static EdgeTtsClient.Result post(String ssml, String key, String region) throws IOException {
        Request req = new Request.Builder()
                .url("https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1")
                .header("Ocp-Apim-Subscription-Key", key)
                .header("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
                .header("User-Agent", "ClinicalMaster")
                .post(RequestBody.create(ssml.getBytes(StandardCharsets.UTF_8),
                        MediaType.parse("application/ssml+xml; charset=utf-8")))
                .build();
        try (Response resp = HTTP.newCall(req).execute()) {
            ResponseBody body = resp.body();
            if (!resp.isSuccessful() || body == null) {
                throw new EdgeTtsClient.ServiceException("Azure HTTP " + resp.code(), resp.code());
            }
            byte[] data = body.bytes();
            if (data.length < 200) throw new EdgeTtsClient.ServiceException("Azure empty audio", resp.code());
            return new EdgeTtsClient.Result(data, new int[0], new int[0], GoogleTtsClient.mp3DurationMs(data));
        }
    }
}
