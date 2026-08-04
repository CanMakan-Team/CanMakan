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
 * Tests vegetarian and vegan approved ingredient mapping behavior.
 *
 * @author YangMaowei
 */
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
    void ignoresNutritionAndUnsupportedDietCodes() {
        DietaryPreferenceChecker checker = checker(Map.of("Milk", Set.of("VEGAN")));

        for (String code : List.of("LOW_SUGAR", "LOW_FAT", "LOW_TRANS_FAT", "LOW_SODIUM", "KETO")) {
            List<Finding> findings = new ArrayList<>();
            checker.check(rule(code), completeProduct(ingredient("Milk")), findings);
            assertTrue(findings.isEmpty());
        }
    }

    @Test
    void permittedVegetarianIngredientAddsNoFinding() {
        DietaryPreferenceChecker checker = checker(Map.of());
        List<Finding> findings = new ArrayList<>();

        checker.check(rule("VEGETARIAN"), completeProduct(ingredient("Milk")), findings);

        assertTrue(findings.isEmpty());
    }

    @Test
    void vegetarianConflictUsesApprovedMapping() {
        DietaryPreferenceChecker checker = checker(Map.of("Chicken", Set.of("VEGETARIAN")));
        List<Finding> findings = new ArrayList<>();

        checker.check(rule("VEGETARIAN"), completeProduct(ingredient("Chicken")), findings);

        assertEquals(1, findings.size());
        assertEquals("Chicken conflicts with the VEGETARIAN restriction.", findings.getFirst().reason());
        assertTrue(findings.getFirst().isConfirmedViolation());
    }

    @Test
    void veganDairyEggAndHoneyConflictsUseApprovedMappings() {
        Map<String, Set<String>> mappings = Map.of(
                "Milk", Set.of("VEGAN"),
                "Egg", Set.of("VEGAN"),
                "Honey", Set.of("VEGAN")
        );
        DietaryPreferenceChecker checker = checker(mappings);

        for (String ingredient : mappings.keySet()) {
            List<Finding> findings = new ArrayList<>();
            checker.check(rule("VEGAN"), completeProduct(ingredient(ingredient)), findings);
            assertEquals(ingredient, findings.getFirst().ingredientName());
            assertEquals("VEGAN", findings.getFirst().restrictionCode());
        }
    }

    @Test
    void oneIngredientCanMapToMultipleRestrictions() {
        DietaryPreferenceChecker checker = checker(Map.of(
                "Animal Gelatine", Set.of("HALAL", "VEGETARIAN", "VEGAN")
        ));

        List<Finding> vegetarian = new ArrayList<>();
        List<Finding> vegan = new ArrayList<>();
        ProductData product = completeProduct(ingredient("Animal Gelatine"));

        checker.check(rule("VEGETARIAN"), product, vegetarian);
        checker.check(rule("VEGAN"), product, vegan);

        assertEquals("VEGETARIAN", vegetarian.getFirst().restrictionCode());
        assertEquals("VEGAN", vegan.getFirst().restrictionCode());
    }

    @Test
    void incompleteIngredientsProduceUncertaintyForEachSupportedRule() {
        DietaryPreferenceChecker checker = checker(Map.of());

        for (String code : List.of("VEGETARIAN", "VEGAN")) {
            List<Finding> findings = new ArrayList<>();
            ProductData product = new ProductData("123", List.of(), null, List.of(), null, false);
            checker.check(rule(code), product, findings);
            assertEquals(FindingType.INCOMPLETE_DATA, findings.getFirst().type());
        }
    }

    @Test
    void preservesExistingFindings() {
        DietaryPreferenceChecker checker = checker(Map.of());
        Finding existing = new Finding(null, null, "Existing finding.", FindingType.INCOMPLETE_DATA);
        List<Finding> findings = new ArrayList<>(List.of(existing));

        checker.check(rule("VEGAN"), completeProduct(ingredient("Oats")), findings);

        assertEquals(List.of(existing), findings);
    }

    private static DietaryPreferenceChecker checker(Map<String, Set<String>> mappings) {
        IngredientRestrictionLookup lookup = name -> mappings.getOrDefault(name, Set.of());
        return new DietaryPreferenceChecker(lookup);
    }

    private static RestrictionRule rule(String code) {
        return new RestrictionRule(code, RestrictionCategory.DIET, RestrictionSeverity.STRICT_AVOID);
    }

    private static Ingredient ingredient(String name) {
        return new Ingredient(name, null, null, false);
    }

    private static ProductData completeProduct(Ingredient ingredient) {
        return new ProductData("123", List.of(ingredient), null, List.of(), null, true);
    }
}
