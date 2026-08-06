package com.canmakan.backend.product.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.knowledgebase.restriction.IngredientRestrictionLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the deterministic HALAL checker.
 *
 * @author YangMaowei
 */
@DisplayName("UC3: ReligiousChecker findings")
class ReligiousCheckerTest {

    private static final RestrictionRule HALAL_RULE = new RestrictionRule(
            "HALAL", RestrictionCategory.RELIGIOUS, RestrictionSeverity.STRICT_AVOID
    );
    private static final String MISSING_CERTIFICATION_REASON =
            "Halal certification information could not be verified from the available product data.";

    @Test
    void supportsOnlyReligiousCategory() {
        ReligiousChecker checker = checker(Map.of());

        assertTrue(checker.supports(RestrictionCategory.RELIGIOUS));
        assertFalse(checker.supports(RestrictionCategory.ALLERGEN));
        assertFalse(checker.supports(RestrictionCategory.DIET));
        assertFalse(checker.supports(null));
    }

    @Test
    void ignoresUnsupportedReligiousCodeAndCategory() {
        ReligiousChecker checker = checker(Map.of());
        List<Finding> findings = new ArrayList<>();

        checker.check(
                new RestrictionRule(
                        "KOSHER", RestrictionCategory.RELIGIOUS,
                        RestrictionSeverity.STRICT_AVOID
                ),
                product(List.of(ingredient("Vegetable Oil")), List.of("halal"), true),
                findings
        );
        checker.check(
                new RestrictionRule(
                        "HALAL", RestrictionCategory.DIET,
                        RestrictionSeverity.STRICT_AVOID
                ),
                product(List.of(ingredient("Vegetable Oil")), List.of("halal"), true),
                findings
        );

        assertEquals(0, findings.size());
    }

    @Test
    void acceptsRecognizedNormalizedHalalTags() {
        ReligiousChecker checker = checker(Map.of());

        for (String tag : List.of("en:halal", "halal")) {
            List<Finding> findings = new ArrayList<>();
            checker.check(
                    HALAL_RULE,
                    product(List.of(ingredient("Vegetable Oil")), List.of(tag), true),
                    findings
            );
            assertEquals(0, findings.size());
        }
    }

    @Test
    void addsFindingWhenHalalTagIsMissing() {
        ReligiousChecker checker = checker(Map.of());
        List<Finding> findings = new ArrayList<>();

        checker.check(
                HALAL_RULE,
                product(List.of(ingredient("Vegetable Oil")), List.of("en:organic"), true),
                findings
        );

        assertEquals(1, findings.size());
        assertFinding(findings.getFirst(), "HALAL", Finding.SUBJECT_LABEL, MISSING_CERTIFICATION_REASON);
    }

    @Test
    void approvedConflictWithoutHalalLabelAddsBothFindings() {
        ReligiousChecker checker = checker(Map.of("Lard", Set.of("HALAL")));
        List<Finding> findings = new ArrayList<>();

        checker.check(
                HALAL_RULE,
                product(List.of(ingredient("Lard")), List.of(), true),
                findings
        );

        assertEquals(2, findings.size());
        assertFinding(
                findings.get(0),
                "HALAL",
                "Lard",
                "Lard conflicts with the HALAL restriction."
        );
        assertFinding(findings.get(1), "HALAL", Finding.SUBJECT_LABEL, MISSING_CERTIFICATION_REASON);
    }

    @Test
    void halalLabelPlusApprovedConflictAddsConflictFinding() {
        ReligiousChecker checker = checker(Map.of("Pork", Set.of("HALAL")));
        List<Finding> findings = new ArrayList<>();

        checker.check(
                HALAL_RULE,
                product(List.of(ingredient("Pork")), List.of("en:halal"), true),
                findings
        );

        assertEquals(1, findings.size());
        assertFinding(
                findings.getFirst(),
                "HALAL",
                "Pork",
                "Pork conflicts with the HALAL restriction."
        );
    }

    @Test
    void incompleteIngredientDataAddsFinding() {
        ReligiousChecker checker = checker(Map.of());
        List<Finding> findings = new ArrayList<>();

        checker.check(HALAL_RULE, product(List.of(), List.of("halal"), false), findings);

        assertEquals(1, findings.size());
        assertFinding(
                findings.getFirst(),
                "HALAL",
                Finding.SUBJECT_UNKNOWN,
                "Ingredient data is incomplete for the HALAL restriction."
        );
    }

    @Test
    void nullAndEmptyLabelsAddMissingCertificationFinding() {
        ReligiousChecker checker = checker(Map.of());

        List<Finding> emptyLabelFindings = new ArrayList<>();
        checker.check(
                HALAL_RULE,
                product(List.of(ingredient("Vegetable Oil")), List.of(), true),
                emptyLabelFindings
        );
        assertEquals(1, emptyLabelFindings.size());
        assertFinding(emptyLabelFindings.getFirst(), "HALAL", Finding.SUBJECT_LABEL, MISSING_CERTIFICATION_REASON);

        List<Finding> nullLabelFindings = new ArrayList<>();
        checker.check(
                HALAL_RULE,
                product(List.of(ingredient("Vegetable Oil")), null, true),
                nullLabelFindings
        );
        assertEquals(1, nullLabelFindings.size());
        assertFinding(nullLabelFindings.getFirst(), "HALAL", Finding.SUBJECT_LABEL, MISSING_CERTIFICATION_REASON);
    }

    @Test
    void preservesExistingFindings() {
        ReligiousChecker checker = checker(Map.of());
        Finding existing = new Finding(null, null, "Existing finding.");
        List<Finding> findings = new ArrayList<>(List.of(existing));

        checker.check(
                HALAL_RULE,
                product(List.of(ingredient("Vegetable Oil")), List.of("halal"), true),
                findings
        );

        assertEquals(1, findings.size());
        assertFinding(findings.getFirst(), null, null, "Existing finding.");
    }

    private static ReligiousChecker checker(Map<String, Set<String>> mappings) {
        IngredientRestrictionLookup lookup = name -> mappings.getOrDefault(name, Set.of());
        return new ReligiousChecker(lookup);
    }

    private static Ingredient ingredient(String name) {
        return new Ingredient(name, null, null, false);
    }

    private static ProductData product(
            List<Ingredient> ingredients,
            List<String> labels,
            boolean complete
    ) {
        return new ProductData("123", ingredients, null, labels, null, complete);
    }

    private static void assertFinding(
            Finding finding,
            String restrictionCode,
            String ingredientName,
            String reason
    ) {
        assertEquals(restrictionCode, finding.restrictionCode());
        assertEquals(ingredientName, finding.ingredientName());
        assertEquals(reason, finding.reason());
    }
}
