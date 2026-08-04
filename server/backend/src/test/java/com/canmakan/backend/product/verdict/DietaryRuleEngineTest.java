package com.canmakan.backend.product.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.knowledgebase.restriction.IngredientRestrictionLookup;
import com.canmakan.backend.product.model.Nutrition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests multi-checker routing and deterministic verdict aggregation.
 *
 * @author YangMaowei
 */
class DietaryRuleEngineTest {

    @Test
    void allergenAndDietaryCheckersExecuteTogether() {
        DietaryRuleEngine engine = engine(Map.of("Milk", Set.of("VEGAN")));
        ProductData product = product(
                List.of(new Ingredient("Milk", "Milk", "DAIRY", false)),
                List.of(),
                completeNutrition(BigDecimal.ZERO),
                true
        );

        SafetyVerdict verdict = engine.assess(
                List.of(
                        rule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID),
                        rule("VEGAN", RestrictionCategory.DIET, RestrictionSeverity.STRICT_AVOID)
                ),
                product
        );

        assertEquals(SafetyVerdict.Level.UNSAFE, verdict.level());
        assertEquals(2, verdict.findings().size());
        assertTrue(verdict.findings().stream().anyMatch(f -> "DAIRY".equals(f.restrictionCode())));
        assertTrue(verdict.findings().stream().anyMatch(f -> "VEGAN".equals(f.restrictionCode())));
    }

    @Test
    void dietCheckersDoNotProduceDuplicateOrUnrelatedFindings() {
        DietaryRuleEngine engine = engine(Map.of());
        ProductData product = product(
                List.of(new Ingredient("Oats", null, null, false)),
                List.of(),
                completeNutrition(new BigDecimal("6")),
                true
        );

        SafetyVerdict verdict = engine.assess(
                List.of(
                        rule("VEGAN", RestrictionCategory.DIET, RestrictionSeverity.STRICT_AVOID),
                        rule("LOW_SUGAR", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)
                ),
                product
        );

        assertEquals(1, verdict.findings().size());
        assertEquals("LOW_SUGAR", verdict.findings().getFirst().restrictionCode());
    }

    @Test
    void missingIngredientsDoNotPreventNutritionEvaluation() {
        DietaryRuleEngine engine = engine(Map.of());
        ProductData product = product(
                List.of(),
                List.of(),
                completeNutrition(new BigDecimal("6")),
                false
        );

        SafetyVerdict verdict = engine.assess(
                List.of(rule("LOW_SUGAR", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)),
                product
        );

        assertEquals(SafetyVerdict.Level.WARNING, verdict.level());
        assertEquals(FindingType.THRESHOLD_EXCEEDED, verdict.findings().getFirst().type());
    }

    @Test
    void confirmedStrictConflictProducesUnsafe() {
        DietaryRuleEngine engine = engine(Map.of("Pork", Set.of("HALAL")));
        ProductData product = product(
                List.of(new Ingredient("Pork", null, null, false)),
                List.of("halal"),
                null,
                true
        );

        SafetyVerdict verdict = engine.assess(
                List.of(rule("HALAL", RestrictionCategory.RELIGIOUS,
                        RestrictionSeverity.STRICT_AVOID)),
                product
        );

        assertEquals(SafetyVerdict.Level.UNSAFE, verdict.level());
    }

    @Test
    void strictMissingCertificationProducesWarningRatherThanUnsafe() {
        DietaryRuleEngine engine = engine(Map.of());
        ProductData product = product(
                List.of(new Ingredient("Oats", null, null, false)),
                List.of(),
                null,
                true
        );

        SafetyVerdict verdict = engine.assess(
                List.of(rule("HALAL", RestrictionCategory.RELIGIOUS,
                        RestrictionSeverity.STRICT_AVOID)),
                product
        );

        assertEquals(SafetyVerdict.Level.WARNING, verdict.level());
        assertEquals(FindingType.MISSING_CERTIFICATION, verdict.findings().getFirst().type());
    }

    @Test
    void noApplicableConflictWithCompleteRequiredDataProducesSafe() {
        DietaryRuleEngine engine = engine(Map.of());
        ProductData product = product(
                List.of(new Ingredient("Oats", null, null, false)),
                List.of(),
                completeNutrition(new BigDecimal("5.0")),
                true
        );

        SafetyVerdict verdict = engine.assess(
                List.of(rule("LOW_SUGAR", RestrictionCategory.DIET,
                        RestrictionSeverity.PREFERENCE)),
                product
        );

        assertEquals(SafetyVerdict.Level.SAFE, verdict.level());
        assertTrue(verdict.findings().isEmpty());
    }

    @Test
    void multipleFindingsAreRetained() {
        DietaryRuleEngine engine = engine(Map.of());
        Nutrition nutrition = new Nutrition(
                new BigDecimal("8"),
                new BigDecimal("0.5"),
                null,
                null,
                null,
                null
        );

        SafetyVerdict verdict = engine.assess(
                List.of(
                        rule("LOW_SUGAR", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE),
                        rule("LOW_SODIUM", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)
                ),
                product(List.of(), List.of(), nutrition, false)
        );

        assertEquals(2, verdict.findings().size());
    }

    @Test
    void unsupportedRuleIsIgnored() {
        DietaryRuleEngine engine = engine(Map.of());

        SafetyVerdict verdict = engine.assess(
                List.of(rule("KETO", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)),
                product(List.of(), List.of(), null, false)
        );

        assertEquals(SafetyVerdict.Level.SAFE, verdict.level());
        assertTrue(verdict.findings().isEmpty());
    }

    private static DietaryRuleEngine engine(Map<String, Set<String>> mappings) {
        IngredientRestrictionLookup lookup = name -> mappings.getOrDefault(name, Set.of());
        List<RestrictionChecker> checkers = List.of(
                new AllergenChecker(),
                new ReligiousChecker(lookup),
                new DietaryPreferenceChecker(lookup),
                new NutritionChecker()
        );
        return new DietaryRuleEngine(checkers, name -> null);
    }

    private static RestrictionRule rule(
            String code,
            RestrictionCategory category,
            RestrictionSeverity severity
    ) {
        return new RestrictionRule(code, category, severity);
    }

    private static ProductData product(
            List<Ingredient> ingredients,
            List<String> labels,
            Nutrition nutrition,
            boolean complete
    ) {
        return new ProductData("123", ingredients, null, labels, nutrition, complete);
    }

    private static Nutrition completeNutrition(BigDecimal sugar) {
        return new Nutrition(sugar, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
