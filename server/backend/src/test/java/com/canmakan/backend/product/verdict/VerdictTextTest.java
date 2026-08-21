package com.canmakan.backend.product.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VerdictTextTest {

    @Test
    void humanizeCodeReplacesSeparatorsAndNull() {
        assertEquals("", VerdictText.humanizeCode(null));
        assertEquals("LOW SUGAR", VerdictText.humanizeCode("LOW_SUGAR"));
        assertEquals("TREE NUT", VerdictText.humanizeCode("TREE-NUT"));
    }

    @Test
    void humanizeTagStripsLanguagePrefix() {
        assertEquals("", VerdictText.humanizeTag(null));
        assertEquals("tree nuts", VerdictText.humanizeTag("en:tree-nuts"));
    }

    @Test
    void humanizePhraseJoinsNonEmptyParts() {
        assertEquals("", VerdictText.humanizePhrase(null));
        assertEquals("", VerdictText.humanizePhrase("  "));
        assertEquals("", VerdictText.humanizePhrase(" , , "));
        assertEquals("tree nuts, milk", VerdictText.humanizePhrase("en:tree-nuts, ,en:milk"));
    }
}
