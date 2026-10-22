package com.ast2012a.clinicalmaster;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AiClient {
    public static final String FIXED_WORKER_URL = "https://aichatsemo.slman2233445566y.workers.dev/";

    public interface Callback {
        void onSuccess(String reply);
        void onError(String message);
    }

    public static void sendMessage(String systemContext, String userMessage, Callback callback) {
        sendMessage(systemContext, userMessage, null, callback);
    }

    public static void sendMessage(String systemContext, String userMessage, String model, Callback callback) {
        sendViaWorker(FIXED_WORKER_URL, systemContext, userMessage, model, callback);
    }

    public static void testWorker(Callback callback) {
        sendViaWorker(FIXED_WORKER_URL, "أجب بكلمة واحدة فقط للتأكد من عمل الاتصال.", "قل: تم الاتصال بنجاح ✅", null, callback);
    }

    public static void classifyIntent(String userMessage, Callback callback) {
        classifyIntent(userMessage, null, callback);
    }

    public static void classifyIntent(String userMessage, String historyContext, Callback callback) {
        boolean hasHistory = historyContext != null && !historyContext.trim().isEmpty();
        sendViaWorker(FIXED_WORKER_URL, AiPrompts.buildRouterPrompt(historyContext),
                "LATEST USER MESSAGE:\n" + userMessage, AiModelSelector.forRouter(hasHistory), callback);
    }

    private static void sendViaWorker(String workerUrl, String systemContext, String userMessage, String model, Callback callback) {
        sendViaWorker(workerUrl, systemContext, userMessage, model, callback, true);
    }

    private static final int RETRY_DELAY_MS = 800;

    private static void sendViaWorker(String workerUrl, String systemContext, String userMessage,
            String model, Callback callback, boolean allowRetry) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(workerUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(15000);

            conn.setReadTimeout(120000);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("system", systemContext == null ? "" : systemContext);
            body.put("message", userMessage);
            if (model != null && !model.trim().isEmpty()) {
                body.put("model", model.trim());
            }

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int status = conn.getResponseCode();
            InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String responseBody = readStream(is);

            if (status < 200 || status >= 300) {
                String serverError = extractWorkerError(responseBody);
                callback.onError(serverError != null
                        ? "رد الووركر بخطأ: " + serverError
                        : "الووركر رجّع HTTP " + status + " بدون تفاصيل إضافية.");
                return;
            }
            if (responseBody == null || responseBody.trim().isEmpty()) {
                callback.onError("الووركر رجّع ردًا فارغًا.");
                return;
            }

            String reply = extractWorkerReply(responseBody);
            if (reply == null || reply.trim().isEmpty()) {
                callback.onError("تعذّر قراءة رد الووركر (شكل غير متوقع). الرد الخام: " + responseBody);
                return;
            }
            callback.onSuccess(tidyReply(reply));
        } catch (Exception e) {
            if (allowRetry) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                sendViaWorker(workerUrl, systemContext, userMessage, model, callback, false);
                return;
            }
            callback.onError("تعذر الوصول لرابط الووركر: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String extractWorkerError(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(responseBody);
            if (json.has("error")) return json.optString("error", null);
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractWorkerReply(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            if (json.has("reply")) {
                return json.optString("reply", null);
            }
            if (json.has("response")) {
                return json.optString("response", null);
            }
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                if (msg != null) return msg.optString("content", null);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String tidyReply(String s) {
        if (s == null || s.isEmpty()) return s;
        String t = s.replace("\r\n", "\n").trim();
        if (t.length() > 6 && t.startsWith("```") && t.endsWith("```") && t.indexOf("```", 3) == t.length() - 3) {
            int nl = t.indexOf('\n');
            t = nl > 0 ? t.substring(nl + 1, t.length() - 3).trim() : t.substring(3, t.length() - 3).trim();
        }
        return t.replaceAll("\n{3,}", "\n\n");
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }
}
