package com.ast2012a.clinicalmaster;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

final class PubMedXml {

    static final class Article {
        String pmid = "";
        String title = "";
        String journal = "";
        String year = "";
        String abstractText = "";
        final List<String> pubTypes = new ArrayList<>();
        int tier = 4;

        String url() {
            return "https://pubmed.ncbi.nlm.nih.gov/" + pmid + "/";
        }

        String evidenceLabel() {
            switch (tier) {
                case 1:
                    return "Systematic review / Meta-analysis";
                case 2:
                    return "Randomized or clinical trial / Guideline";
                case 3:
                    return "Review";
                default:
                    return "Primary study / other";
            }
        }
    }

    private static final Pattern DOCTYPE = Pattern.compile("(?s)<!DOCTYPE[^>]*>");
    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");

    private PubMedXml() {}

    static List<Article> parse(String xml) {
        List<Article> result = new ArrayList<>();
        if (xml == null || xml.trim().isEmpty()) return result;
        try {
            String cleaned = DOCTYPE.matcher(xml).replaceAll("");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            Document doc = builder.parse(new InputSource(new StringReader(cleaned)));
            NodeList articles = doc.getElementsByTagName("PubmedArticle");
            for (int i = 0; i < articles.getLength(); i++) {
                Node node = articles.item(i);
                if (!(node instanceof Element)) continue;
                Article a = readArticle((Element) node);
                if (a != null) result.add(a);
            }
        } catch (Exception ignored) {
            return result;
        }
        return result;
    }

    private static Article readArticle(Element el) {
        Article a = new Article();
        a.pmid = firstText(el, "PMID");
        a.title = clean(firstText(el, "ArticleTitle"));
        a.journal = clean(firstText(el, "ISOAbbreviation"));
        if (a.journal.isEmpty()) a.journal = clean(firstText(el, "Title"));

        NodeList abs = el.getElementsByTagName("AbstractText");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < abs.getLength(); i++) {
            Node n = abs.item(i);
            String body = clean(n.getTextContent());
            if (body.isEmpty()) continue;
            String label = n instanceof Element ? ((Element) n).getAttribute("Label") : "";
            if (sb.length() > 0) sb.append(' ');
            if (label != null && !label.trim().isEmpty()) sb.append(label.trim()).append(": ");
            sb.append(body);
        }
        a.abstractText = sb.toString();

        NodeList types = el.getElementsByTagName("PublicationType");
        for (int i = 0; i < types.getLength(); i++) {
            String t = clean(types.item(i).getTextContent());
            if (!t.isEmpty()) a.pubTypes.add(t);
        }
        a.year = readYear(el);
        a.tier = tierOf(a.pubTypes);

        if (a.pmid.isEmpty() || a.title.isEmpty() || a.abstractText.length() < 80) return null;
        return a;
    }

    private static String readYear(Element el) {
        NodeList pubDates = el.getElementsByTagName("PubDate");
        if (pubDates.getLength() > 0 && pubDates.item(0) instanceof Element) {
            Element pd = (Element) pubDates.item(0);
            String y = firstText(pd, "Year");
            if (!y.isEmpty()) return y;
            Matcher m = YEAR.matcher(firstText(pd, "MedlineDate"));
            if (m.find()) return m.group();
        }
        NodeList articleDates = el.getElementsByTagName("ArticleDate");
        if (articleDates.getLength() > 0 && articleDates.item(0) instanceof Element) {
            return firstText((Element) articleDates.item(0), "Year");
        }
        return "";
    }

    static int tierOf(List<String> types) {
        int best = 4;
        for (String raw : types) {
            String t = raw.toLowerCase(Locale.ROOT);
            int tier = 4;
            if (t.contains("meta-analysis") || t.contains("systematic review")) tier = 1;
            else if (t.contains("randomized controlled trial") || t.contains("practice guideline")
                    || t.equals("guideline") || t.contains("clinical trial")) tier = 2;
            else if (t.equals("review")) tier = 3;
            if (tier < best) best = tier;
        }
        return best;
    }

    static void rank(List<Article> articles) {
        Collections.sort(articles, new Comparator<Article>() {
            @Override
            public int compare(Article x, Article y) {
                if (x.tier != y.tier) return x.tier - y.tier;
                return y.year.compareTo(x.year);
            }
        });
    }

    private static String firstText(Element el, String tag) {
        NodeList list = el.getElementsByTagName(tag);
        if (list.getLength() == 0) return "";
        String t = list.item(0).getTextContent();
        return t == null ? "" : t.trim();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
