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
 * Tests vegetarian and vegan approved ingredient mapping behavior.
 *
 * @author YangMaowei
 */
@DisplayName("UC3: DietaryPreferenceChecker findings")
class DietaryPreferenceCheckerTest {

    @Test
    void supportsDietCategoryOnly() {
        DietaryPreferenceChecker checker = checker(Map.of());

        assertTrue(checker.supports(RestrictionCategory.DIET));
        assertFalse(checker.supports(RestrictionCategory.ALLERGEN));
        assertFalse(checker.supports(RestrictionCategory.RELIGIOUS));
        assertFalse(checker.supports(null));
    }

    @Test
    void handlesVegetarianAndVeganCodesWithoutInferringConflicts() {
        DietaryPreferenceChecker checker = checker(Map.of());

        for (String code : List.of("VEGETARIAN", "VEGAN")) {
            List<Finding> findings = new ArrayList<>();
            checker.check(rule(code), completeProduct(ingredient("Oats")), findings);
            assertEquals(0, findings.size());
        }
    }

    @Test
    void ignoresNutritionAndUnknownDietCodes() {
        IngredientRestrictionLookup unusedLookup = name -> {
            throw new AssertionError("Lookup should not be called for unsupported codes");
        };
        DietaryPreferenceChecker checker = new DietaryPreferenceChecker(unusedLookup);

        for (String code : List.of(
                "LOW_SUGAR", "LOW_FAT", "LOW_TRANS_FAT", "LOW_SODIUM", "KETO"
        )) {
            List<Finding> findings = new ArrayList<>();
            checker.check(rule(code), completeProduct(ingredient("Milk")), findings);
            assertEquals(0, findings.size());
        }
    }

    @Test
    void approvedVegetarianConflictAddsFinding() {
        DietaryPreferenceChecker checker = checker(Map.of("Chicken", Set.of("VEGETARIAN")));
        List<Finding> findings = new ArrayList<>();

        checker.check(rule("VEGETARIAN"), completeProduct(ingredient("Chicken")), findings);

        assertEquals(1, findings.size());
        assertFinding(
                findings.getFirst(),
                "VEGETARIAN",
                "Chicken",
                "Chicken conflicts with the VEGETARIAN restriction."
        );
    }

    @Test
    void approvedVeganConflictAddsFinding() {
        DietaryPreferenceChecker checker = checker(Map.of("Milk", Set.of("VEGAN")));
        List<Finding> findings = new ArrayList<>();

        checker.check(rule("VEGAN"), completeProduct(ingredient("Milk")), findings);

        assertEquals(1, findings.size());
        assertFinding(
                findings.getFirst(),
                "VEGAN",
                "Milk",
                "Milk conflicts with the VEGAN restriction."
        );
    }

    @Test
    void oneIngredientCanMapToMultipleRestrictions() {
        DietaryPreferenceChecker checker = checker(Map.of(
                "Animal Gelatine", Set.of("HALAL", "VEGETARIAN", "VEGAN")
        ));
        ProductData product = completeProduct(ingredient("Animal Gelatine"));

        List<Finding> vegetarianFindings = new ArrayList<>();
        checker.check(rule("VEGETARIAN"), product, vegetarianFindings);
        assertEquals(1, vegetarianFindings.size());
        assertFinding(
                vegetarianFindings.getFirst(),
                "VEGETARIAN",
                "Animal Gelatine",
                "Animal Gelatine conflicts with the VEGETARIAN restriction."
        );

        List<Finding> veganFindings = new ArrayList<>();
        checker.check(rule("VEGAN"), product, veganFindings);
        assertEquals(1, veganFindings.size());
        assertFinding(
                veganFindings.getFirst(),
                "VEGAN",
                "Animal Gelatine",
                "Animal Gelatine conflicts with the VEGAN restriction."
        );
    }

    @Test
    void incompleteIngredientDataAddsFinding() {
        DietaryPreferenceChecker checker = checker(Map.of());

        for (String code : List.of("VEGETARIAN", "VEGAN")) {
            List<Finding> findings = new ArrayList<>();
            checker.check(
                    rule(code),
                    new ProductData("123", List.of(), null, List.of(), null, false),
                    findings
            );

            assertEquals(1, findings.size());
            assertFinding(
                    findings.getFirst(),
                    code,
                    Finding.SUBJECT_UNKNOWN,
                    "Ingredient data is incomplete for the " + code + " restriction."
            );
        }
    }

    @Test
    void nullIngredientsAddIncompleteDataFinding() {
        DietaryPreferenceChecker checker = checker(Map.of());
        List<Finding> findings = new ArrayList<>();

        checker.check(
                rule("VEGAN"),
                new ProductData("123", null, null, List.of(), null, true),
                findings
        );

        assertEquals(1, findings.size());
        assertFinding(
                findings.getFirst(),
                "VEGAN",
                Finding.SUBJECT_UNKNOWN,
                "Ingredient data is incomplete for the VEGAN restriction."
        );
    }

    @Test
    void preservesExistingFindings() {
        DietaryPreferenceChecker checker = checker(Map.of());
        Finding existing = new Finding(null, null, "Existing finding.");
        List<Finding> findings = new ArrayList<>(List.of(existing));

        checker.check(rule("VEGAN"), completeProduct(ingredient("Oats")), findings);

        assertEquals(1, findings.size());
        assertFinding(findings.getFirst(), null, null, "Existing finding.");
    }

    private static DietaryPreferenceChecker checker(Map<String, Set<String>> mappings) {
        IngredientRestrictionLookup lookup = name -> mappings.getOrDefault(name, Set.of());
        return new DietaryPreferenceChecker(lookup);
    }

    private static RestrictionRule rule(String code) {
        return new RestrictionRule(
                code,
                RestrictionCategory.DIET,
                RestrictionSeverity.STRICT_AVOID
        );
    }

    private static Ingredient ingredient(String name) {
        return new Ingredient(name, null, null, false);
    }

    private static ProductData completeProduct(Ingredient ingredient) {
        return new ProductData("123", List.of(ingredient), null, List.of(), null, true);
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
