package com.canmakan.backend.knowledgebase.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IngredientLabelParserTest {

    @Test
    void splitsTopLevelCommas() {
        assertEquals(
            List.of("Rice flour", "Salt", "Sugar"),
            IngredientLabelParser.split("Rice flour, Salt, Sugar"));
    }

    @Test
    void keepsCommasInsideParentheses() {
        assertEquals(
            List.of("Oyster Extract (Oysters, Water, Salt)", "Sugar", "Modified Corn Starch"),
            IngredientLabelParser.split(
                "Oyster Extract (Oysters, Water, Salt), Sugar, Modified Corn Starch"));
    }

    @Test
    void rejoinsFragmentsSplitInsideParentheses() {
        assertEquals(
            List.of("Oyster Extract (Oysters, Water, Salt)", "Sugar", "Modified Corn Starch"),
            IngredientLabelParser.normalize(List.of(
                "Oyster Extract (Oysters",
                "Water",
                "Salt)",
                "Sugar",
                "Modified Corn Starch")));
    }

    @Test
    void splitsASingleEntryThatStillContainsTopLevelCommas() {
        assertEquals(
            List.of("Oyster Extract (Oysters, Water, Salt)", "Sugar"),
            IngredientLabelParser.normalize(
                List.of("Oyster Extract (Oysters, Water, Salt), Sugar")));
    }

    @Test
    void ignoresNullAndBlank() {
        assertTrue(IngredientLabelParser.split("  ").isEmpty());
        assertTrue(IngredientLabelParser.normalize(java.util.Arrays.asList(" ", null, "")).isEmpty());
    }
}
