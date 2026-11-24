package com.ast2012a.clinicalmaster;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PubMedClient {
    public static class Result {
        public String title;
        public String extract;
        public String sourceUrl;
    }

    private static final String ESEARCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    private static final String EFETCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi";
    private static final String TOOL_TAG = "ast2012a_clinicalmaster";
    private static final int TIMEOUT_MS = 8000;

    public static Result search(String query) {
        if (!OfflineMode.isOnlineAllowed()) return null;
        if (query == null || query.trim().isEmpty()) return null;
        try {
            String pmid = findBestPmid(query.trim());
            if (pmid == null) return null;

            String fetchUrl = EFETCH_URL + "?db=pubmed&rettype=abstract&retmode=text&id="
                    + URLEncoder.encode(pmid, "UTF-8") + "&tool=" + TOOL_TAG;
            String rawText = httpGet(fetchUrl);
            if (rawText == null || rawText.trim().isEmpty()) return null;

            return parseAbstractText(rawText, pmid);
        } catch (Exception e) {
            return null;
        }
    }

    static List<PubMedXml.Article> searchMany(String query, int maxResults) {
        List<PubMedXml.Article> empty = new ArrayList<>();
        if (!OfflineMode.isOnlineAllowed()) return empty;
        if (query == null || query.trim().isEmpty() || maxResults < 1) return empty;
        try {
            String term = URLEncoder.encode(query.trim() + " AND hasabstract", "UTF-8");
            String searchUrl = ESEARCH_URL + "?db=pubmed&retmode=json&sort=relevance&retmax=" + maxResults
                    + "&term=" + term + "&tool=" + TOOL_TAG;
            String searchJson = httpGet(searchUrl);
            if (searchJson == null) return empty;
            JSONObject root = new JSONObject(searchJson);
            JSONObject esearchresult = root.optJSONObject("esearchresult");
            JSONArray idList = esearchresult != null ? esearchresult.optJSONArray("idlist") : null;
            if (idList == null || idList.length() == 0) return empty;
            StringBuilder ids = new StringBuilder();
            for (int i = 0; i < idList.length(); i++) {
                String id = idList.optString(i, "");
                if (id.isEmpty()) continue;
                if (ids.length() > 0) ids.append(',');
                ids.append(id);
            }
            if (ids.length() == 0) return empty;
            String fetchUrl = EFETCH_URL + "?db=pubmed&retmode=xml&rettype=abstract&id="
                    + URLEncoder.encode(ids.toString(), "UTF-8") + "&tool=" + TOOL_TAG;
            return PubMedXml.parse(httpGet(fetchUrl));
        } catch (Exception e) {
            return empty;
        }
    }

    private static String findBestPmid(String query) throws IOException {
        String encodedQuery = URLEncoder.encode(query, "UTF-8");
        String searchUrl = ESEARCH_URL + "?db=pubmed&retmode=json&retmax=1&sort=relevance&term="
                + encodedQuery + "&tool=" + TOOL_TAG;
        String searchJson = httpGet(searchUrl);
        if (searchJson == null) return null;

        try {
            JSONObject root = new JSONObject(searchJson);
            JSONObject esearchresult = root.optJSONObject("esearchresult");
            JSONArray idList = esearchresult != null ? esearchresult.optJSONArray("idlist") : null;
            if (idList == null || idList.length() == 0) return null;
            String pmid = idList.optString(0, null);
            return (pmid == null || pmid.trim().isEmpty()) ? null : pmid.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static Result parseAbstractText(String rawText, String pmid) {
        String[] rawParagraphs = rawText.split("\\n\\s*\\n");
        List<String> paragraphs = new ArrayList<>();
        for (String p : rawParagraphs) {
            String t = p.trim().replaceAll("\\s+", " ");
            if (!t.isEmpty()) paragraphs.add(t);
        }
        if (paragraphs.size() < 2) return null;

        String title = paragraphs.get(1);
        if (title.length() > 300) title = title.substring(0, 300);

        String bestAbstract = null;
        for (int i = 2; i < paragraphs.size(); i++) {
            String p = paragraphs.get(i);
            String lower = p.toLowerCase(Locale.ROOT);
            if (lower.contains("author information")) continue;
            if (lower.startsWith("doi:") || lower.startsWith("pmid:") || lower.startsWith("pmcid:")
                    || lower.startsWith("©") || lower.startsWith("copyright")) continue;

            if (p.length() < 150) continue;
            if (bestAbstract == null || p.length() > bestAbstract.length()) bestAbstract = p;
        }
        if (bestAbstract == null || bestAbstract.trim().isEmpty()) return null;

        Result r = new Result();
        r.title = title;
        r.extract = bestAbstract.length() > 2500 ? bestAbstract.substring(0, 2500) + "…" : bestAbstract;
        r.sourceUrl = "https://pubmed.ncbi.nlm.nih.gov/" + pmid + "/";
        return r;
    }

    private static String httpGet(String urlStr) {
        if (!OfflineMode.isOnlineAllowed()) return null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            conn.setRequestProperty("User-Agent", "AST2012A-ClinicalMaster-Android/1.0 (contact: app-only)");

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) return null;

            InputStream is = conn.getInputStream();
            return readStream(is);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }
}
