package com.ast2012a.clinicalmaster;

public class ChatMessage {
    public static final int ROLE_USER = 0;
    public static final int ROLE_AI = 1;

    public int role;
    public String text;
    public String sourceLabel;
    public String sourceUrl;
    public String relatedQuery;
    public long timestamp;

    public ChatMessage(int role, String text) {
        this(role, text, null, null, null);
    }

    public ChatMessage(int role, String text, String sourceLabel, String sourceUrl, String relatedQuery) {
        this.role = role;
        this.text = text;
        this.sourceLabel = sourceLabel;
        this.sourceUrl = sourceUrl;
        this.relatedQuery = relatedQuery;
        this.timestamp = System.currentTimeMillis();
    }
}
