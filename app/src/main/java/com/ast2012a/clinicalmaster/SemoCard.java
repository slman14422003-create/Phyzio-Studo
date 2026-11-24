package com.ast2012a.clinicalmaster;

import java.util.ArrayList;
import java.util.List;

public class SemoCard {
    public enum Status { DRAFT, APPROVED }

    public static final class Version {
        public final String answer;
        public final long timestamp;

        public Version(String answer, long timestamp) {
            this.answer = answer == null ? "" : answer;
            this.timestamp = timestamp;
        }
    }

    public String id = "";
    public String question = "";
    public List<String> keywords = new ArrayList<>();
    public String answer = "";
    public String source = "";
    public Status status = Status.DRAFT;
    public long approvedAt = 0L;
    public long createdAt = 0L;
    public List<Version> history = new ArrayList<>();

    public boolean isApproved() {
        return status == Status.APPROVED;
    }

    public SemoCard copy() {
        SemoCard c = new SemoCard();
        c.id = id;
        c.question = question;
        c.keywords = new ArrayList<>(keywords);
        c.answer = answer;
        c.source = source;
        c.status = status;
        c.approvedAt = approvedAt;
        c.createdAt = createdAt;
        c.history = new ArrayList<>(history);
        return c;
    }
}
