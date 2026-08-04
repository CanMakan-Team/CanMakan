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
import org.junit.jupiter.api.Test;

/**
 * Tests the deterministic HALAL checker.
 *
 * @author YangMaowei
 */
class ReligiousCheckerTest {

    private static final RestrictionRule HALAL_RULE = new RestrictionRule(
            "HALAL", RestrictionCategory.RELIGIOUS, RestrictionSeverity.STRICT_AVOID
    );

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
                new RestrictionRule("KOSHER", RestrictionCategory.RELIGIOUS,
                        RestrictionSeverity.STRICT_AVOID),
                product(List.of(ingredient("Vegetable Oil")), List.of("halal"), true),
                findings
        );
        checker.check(
                new RestrictionRule("HALAL", RestrictionCategory.DIET,
                        RestrictionSeverity.STRICT_AVOID),
                product(List.of(ingredient("Vegetable Oil")), List.of("halal"), true),
                findings
        );

        assertTrue(findings.isEmpty());
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
            assertTrue(findings.isEmpty());
        }
    }

    @Test
    void addsUncertaintyWhenHalalTagIsMissing() {
        ReligiousChecker checker = checker(Map.of());
        List<Finding> findings = new ArrayList<>();

        checker.check(
                HALAL_RULE,
                product(List.of(ingredient("Vegetable Oil")), List.of("en:organic"), true),
                findings
        );

        assertEquals(1, findings.size());
        assertEquals(FindingType.MISSING_CERTIFICATION, findings.getFirst().type());
        assertEquals(
                "Halal certification information could not be verified from the available product data.",
                findings.getFirst().reason()
        );
    }

    @Test
    void approvedIngredientConflictOverridesHalalLabel() {
        ReligiousChecker checker = checker(Map.of("Pork", Set.of("HALAL")));
        List<Finding> findings = new ArrayList<>();

        checker.check(
                HALAL_RULE,
                product(List.of(ingredient("Pork")), List.of("en:halal"), true),
                findings
        );

        assertEquals(1, findings.size());
        assertEquals(FindingType.CONFIRMED_CONFLICT, findings.getFirst().type());
        assertEquals("Pork conflicts with the HALAL restriction.", findings.getFirst().reason());
    }

    @Test
    void approvedConflictIsFoundWithoutHalalLabel() {
        ReligiousChecker checker = checker(Map.of("Lard", Set.of("HALAL")));
        List<Finding> findings = new ArrayList<>();

        checker.check(
                HALAL_RULE,
                product(List.of(ingredient("Lard")), List.of(), true),
                findings
        );

        assertEquals(2, findings.size());
        assertTrue(findings.stream().anyMatch(Finding::isConfirmedViolation));
        assertTrue(findings.stream().anyMatch(f -> f.type() == FindingType.MISSING_CERTIFICATION));
    }

    @Test
    void incompleteIngredientDataProducesUncertainty() {
        ReligiousChecker checker = checker(Map.of());
        List<Finding> findings = new ArrayList<>();

        checker.check(HALAL_RULE, product(List.of(), List.of("halal"), false), findings);

        assertEquals(1, findings.size());
        assertEquals(FindingType.INCOMPLETE_DATA, findings.getFirst().type());
    }

    @Test
    void nullAndEmptyLabelsProduceMissingCertificationFinding() {
        ReligiousChecker checker = checker(Map.of());

        for (List<String> labels : List.of(List.<String>of())) {
            List<Finding> findings = new ArrayList<>();
            checker.check(
                    HALAL_RULE,
                    product(List.of(ingredient("Vegetable Oil")), labels, true),
                    findings
            );
            assertEquals(FindingType.MISSING_CERTIFICATION, findings.getFirst().type());
        }

        List<Finding> nullLabelFindings = new ArrayList<>();
        checker.check(
                HALAL_RULE,
                product(List.of(ingredient("Vegetable Oil")), null, true),
                nullLabelFindings
        );
        assertEquals(FindingType.MISSING_CERTIFICATION, nullLabelFindings.getFirst().type());
    }

    @Test
    void preservesExistingFindings() {
        ReligiousChecker checker = checker(Map.of());
        Finding existing = new Finding(null, null, "Existing finding.", FindingType.INCOMPLETE_DATA);
        List<Finding> findings = new ArrayList<>(List.of(existing));

        checker.check(
                HALAL_RULE,
                product(List.of(ingredient("Vegetable Oil")), List.of("halal"), true),
                findings
        );

        assertEquals(List.of(existing), findings);
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
}
