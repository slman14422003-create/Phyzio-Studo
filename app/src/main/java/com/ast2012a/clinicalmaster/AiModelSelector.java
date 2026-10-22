package com.ast2012a.clinicalmaster;

import java.util.Locale;

final class AiModelSelector {
    private AiModelSelector() {}

    static final String MODEL_FAST = "@cf/meta/llama-3.1-8b-instruct";

    static final String MODEL_CLINICAL = "@cf/meta/llama-3.3-70b-instruct";

    private static final String[] MEDICAL_HINTS = {
            "ألم", "وجع", "علاج", "تمرين", "تمارين", "بروتوكول", "تشخيص",
            "إصابة", "اصابة", "عضلة", "عضلات", "مفصل", "مفاصل", "عصب", "أعصاب",
            "تأهيل", "جلسة", "جلسات", "مريض", "مرضى", "كهربائي", "تحفيز",
            "خزل", "شلل", "انزلاق", "غضروف", "التهاب", "كسر", "جبيرة",
            "فقرات", "عمود فقري", "ركبة", "كتف", "ظهر", "رقبة", "قدم",
            "TENS", "EMS", "فيزيوثيرابي", "علاج طبيعي", "عرق النسا"
    };

    static boolean mentionsMedicalContent(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String t = text.toLowerCase(Locale.ROOT);
        for (String hint : MEDICAL_HINTS) {
            if (t.contains(hint.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    static String forRouter() {
        return MODEL_FAST;
    }

    static String forRouter(boolean hasHistory) {
        return hasHistory ? MODEL_CLINICAL : MODEL_FAST;
    }

    static String forChat(String userText) {
        return mentionsMedicalContent(userText) ? MODEL_CLINICAL : MODEL_FAST;
    }

    static String forChat(String userText, boolean hasHistory) {
        return hasHistory ? MODEL_CLINICAL : forChat(userText);
    }

    static String forClinical() {
        return MODEL_CLINICAL;
    }
}
