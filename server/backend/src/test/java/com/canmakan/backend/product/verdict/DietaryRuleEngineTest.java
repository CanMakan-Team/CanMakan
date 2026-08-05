package com.canmakan.backend.product.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers incomplete / unresolved finding codes returned on {@code POST /api/scan/assess}.
 *
 * @author Amelia
 */
@DisplayName("UC2: DietaryRuleEngine data-quality findings")
class DietaryRuleEngineTest {

    private IngredientResolver resolver;
    private DietaryRuleEngine engine;

    @BeforeEach
    void setUp() {
        resolver = mock(IngredientResolver.class);
        engine = new DietaryRuleEngine(List.of(), resolver);
    }

    @Test
    @DisplayName("Incomplete product data uses INCOMPLETE_DATA / unknown, not nulls")
    void incompleteProductUsesNonNullFindingFields() {
        SafetyVerdict verdict = engine.assess(List.of(), null);

        assertEquals(SafetyVerdict.Level.WARNING, verdict.level());
        Finding finding = verdict.findings().getFirst();
        assertEquals(DietaryRuleEngine.INCOMPLETE_DATA, finding.restrictionCode());
        assertEquals(Finding.SUBJECT_UNKNOWN, finding.ingredientName());
        assertFalse(finding.reason().isBlank());
    }

    @Test
    @DisplayName("Unresolved ingredients emit one UNRESOLVED finding per ingredient")
    void unresolvedIngredientsUseNonNullFindingFields() {
        Ingredient casein = new Ingredient("Casein", null, null, false);
        Ingredient e471 = new Ingredient("E471", null, null, true);
        when(resolver.resolveRootAllergen("Casein")).thenReturn(null);
        when(resolver.resolveRootAllergen("E471")).thenReturn(null);

        ProductData product = new ProductData(
                "4901330305840",
                List.of(casein, e471),
                "Casein, E471",
                List.of(),
                null,
                true
        );

        SafetyVerdict verdict = engine.assess(List.of(), product);

        assertEquals(SafetyVerdict.Level.WARNING, verdict.level());
        assertEquals(2, verdict.findings().size());
        assertEquals(DietaryRuleEngine.UNRESOLVED, verdict.findings().get(0).restrictionCode());
        assertEquals("Casein", verdict.findings().get(0).ingredientName());
        assertEquals(DietaryRuleEngine.UNRESOLVED, verdict.findings().get(1).restrictionCode());
        assertEquals("E471", verdict.findings().get(1).ingredientName());
    }

    @Test
    @DisplayName("Resolved ingredients with no rule hits stay SAFE with empty findings")
    void resolvedIngredientsStaySafe() {
        Ingredient milk = new Ingredient("Milk", null, "DAIRY", false);
        ProductData product = new ProductData(
                "123",
                List.of(milk),
                "Milk",
                List.of(),
                null,
                true
        );

        SafetyVerdict verdict = engine.assess(
                List.of(new RestrictionRule("PEANUT", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)),
                product
        );

        assertEquals(SafetyVerdict.Level.SAFE, verdict.level());
        assertEquals(List.of(), verdict.findings());
    }
}
