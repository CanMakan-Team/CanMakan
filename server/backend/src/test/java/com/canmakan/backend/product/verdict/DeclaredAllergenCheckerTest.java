package com.canmakan.backend.product.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UC3: DeclaredAllergenChecker")
class DeclaredAllergenCheckerTest {

    private DeclaredAllergenChecker checker;

    @BeforeEach
    void setUp() {
        checker = new DeclaredAllergenChecker();
    }

    @Test
    void mapsDeclaredMilkTagToDairyRestriction() {
        List<Finding> hits = new ArrayList<>();
        checker.check(
                rule("DAIRY"),
                product(List.of("en:milk")),
                hits);

        assertEquals(1, hits.size());
        assertEquals("DAIRY", hits.getFirst().restrictionCode());
        assertEquals(Finding.SUBJECT_LABEL, hits.getFirst().ingredientName());
    }

    @Test
    void ignoresUnrelatedDeclaredAllergenTags() {
        List<Finding> hits = new ArrayList<>();
        checker.check(
                rule("DAIRY"),
                product(List.of("en:gluten")),
                hits);

        assertTrue(hits.isEmpty());
    }

    private static RestrictionRule rule(String code) {
        return new RestrictionRule(code, RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE);
    }

    private static ProductData product(List<String> labelTags) {
        return new ProductData(
                "4908013129717",
                List.of(),
                "100% Fresh Milk made in Hokkaido)",
                labelTags,
                List.of(),
                null,
                true
        );
    }
}
