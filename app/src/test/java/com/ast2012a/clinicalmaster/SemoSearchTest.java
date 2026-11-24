package com.ast2012a.clinicalmaster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SemoSearchTest {

    private static SemoCard card(String id, String q, String kw, String a, SemoCard.Status st) {
        SemoCard c = new SemoCard();
        c.id = id;
        c.question = q;
        if (kw != null) c.keywords = new ArrayList<>(Arrays.asList(kw.split(",")));
        c.answer = a;
        c.status = st;
        return c;
    }

    @Test
    public void normalizeStripsDiacriticsAndTatweel() {
        assertEquals(SemoSearch.normalize("الرَّكبــة"), SemoSearch.normalize("الركبه"));
    }

    @Test
    public void normalizeUnifiesAlefHamzaYaaTaaMarbuta() {
        assertEquals(SemoSearch.normalize("أِلتهاب"), SemoSearch.normalize("التهاب"));
        assertEquals(SemoSearch.normalize("مسؤول"), SemoSearch.normalize("مسوول"));
        assertEquals(SemoSearch.normalize("على"), SemoSearch.normalize("علي"));
        assertEquals(SemoSearch.normalize("عضلة"), SemoSearch.normalize("عضله"));
    }

    @Test
    public void normalizeDigitsAndLatin() {
        assertEquals("نصف 12 ml", SemoSearch.normalize("نصف ١٢ ML"));
        assertEquals("tens 3", SemoSearch.normalize("TENS ٣"));
    }

    @Test
    public void stemmingMergesForms() {
        assertEquals(SemoSearch.tokenize("الركبة"), SemoSearch.tokenize("ركبتي"));
        assertEquals(SemoSearch.tokenize("ركبة"), SemoSearch.tokenize("الركبه"));
    }

    @Test
    public void matchesApprovedCardDespiteDiacriticsAndHamza() {
        List<SemoCard> cards = new ArrayList<>();
        cards.add(card("1", "ما علاج التهاب أوتار الركبة؟", "التهاب,وتر,ركبة",
                "راحة وكمادات باردة.", SemoCard.Status.APPROVED));
        cards.add(card("2", "ما هي أسباب ألم الكتف", "كتف,ألم",
                "نص آخر.", SemoCard.Status.APPROVED));
        SemoSearch.Result r = SemoSearch.search(cards, "عِلاج إلتهاب اوتار الرُكبه");
        assertNotNull(r);
        assertEquals("1", r.card.id);
        assertTrue(r.score > SemoSearch.MIN_SCORE);
    }

    @Test
    public void draftsAreNeverReturned() {
        List<SemoCard> cards = new ArrayList<>();
        cards.add(card("1", "علاج التهاب أوتار الركبة", "ركبة,التهاب", "نص", SemoCard.Status.DRAFT));
        assertNull(SemoSearch.search(cards, "علاج التهاب أوتار الركبة"));
    }

    @Test
    public void unrelatedQueryReturnsNoMatch() {
        List<SemoCard> cards = new ArrayList<>();
        cards.add(card("1", "علاج التهاب أوتار الركبة", "ركبة,التهاب", "راحة وكمادات", SemoCard.Status.APPROVED));
        assertNull(SemoSearch.search(cards, "ما هو سعر الدولار اليوم"));
        assertNull(SemoSearch.search(cards, ""));
    }

    @Test
    public void answerOnlyMatchIsRejected() {
        List<SemoCard> cards = new ArrayList<>();
        cards.add(card("1", "علاج التهاب أوتار الركبة", "ركبة", "راحة وكمادات باردة وبروتوكول معين",
                SemoCard.Status.APPROVED));
        assertNull(SemoSearch.search(cards, "كمادات باردة بروتوكول"));
    }
}
