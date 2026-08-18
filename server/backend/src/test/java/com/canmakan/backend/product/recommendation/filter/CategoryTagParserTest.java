package com.canmakan.backend.product.recommendation.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UC5: CategoryTagParser")
class CategoryTagParserTest {

    @Test
    void parseTagsSplitsCommaSeparatedValues() {
        Set<String> tags = CategoryTagParser.parseTags(
                "en:dairy-substitutes,en:milk-substitutes, en:oat-based-drinks");

        assertEquals(3, tags.size());
        assertTrue(tags.contains("en:dairy-substitutes"));
        assertTrue(tags.contains("en:milk-substitutes"));
        assertTrue(tags.contains("en:oat-based-drinks"));
    }

    @Test
    void parseTagsReturnsEmptyForBlankInput() {
        assertTrue(CategoryTagParser.parseTags(null).isEmpty());
        assertTrue(CategoryTagParser.parseTags("").isEmpty());
        assertTrue(CategoryTagParser.parseTags("   ").isEmpty());
    }

    @Test
    void containsAnyMatchesNeedleInTagSet() {
        Set<String> tags = CategoryTagParser.parseTags("en:milk-substitutes,en:oat-based-drinks");

        assertTrue(CategoryTagParser.containsAny(tags, List.of("en:oat-based-drinks")));
        assertFalse(CategoryTagParser.containsAny(tags, List.of("en:coconut-milks-and-creams")));
    }

    @Test
    void containsTagChecksRawCategoryTagsString() {
        assertTrue(CategoryTagParser.containsTag(
                "en:milk-substitutes,en:plant-based-creams-for-cooking",
                "en:plant-based-creams-for-cooking"));
    }

    @Test
    void toCategoryTagStripsLeadingAndTrailingDashesFromNonAlphanumericEdges() {
        assertEquals("en:weird-category", CategoryTagParser.toCategoryTag("!!Weird Category??"));
    }

    @Test
    void toCategoryTagLeavesCleanInputUnchanged() {
        assertEquals("en:brown-rice-flour", CategoryTagParser.toCategoryTag("Brown Rice Flour"));
    }

    @Test
    void containsAnyIncludingMainCategoryMatchesSlugFromEnglishCategory() {
        assertTrue(CategoryTagParser.containsAnyIncludingMainCategory(
                "en:no-gluten,en:gluten-free,en:gluten-free-flour",
                "Brown Rice Flour",
                List.of("en:brown-rice-flour")));
        assertFalse(CategoryTagParser.containsAnyIncludingMainCategory(
                "en:no-gluten,en:gluten-free,en:gluten-free-flour",
                "Brown Rice Flour",
                List.of("en:oat-flour")));
    }
}
