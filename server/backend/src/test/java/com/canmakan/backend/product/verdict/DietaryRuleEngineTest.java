package com.canmakan.backend.product.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.knowledgebase.mcp.contract.CrossContaminationResult;
import com.canmakan.backend.knowledgebase.mcp.contract.DietaryRuleResult;
import com.canmakan.backend.knowledgebase.mcp.server.CrossContaminationTool;
import com.canmakan.backend.knowledgebase.mcp.server.DietaryRuleTool;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers incomplete / unresolved / known-safe finding behaviour for assess,
 * plus dietary-rule filtering and cross-contamination WARNING findings.
 *
 * @author Amelia
 */
@DisplayName("UC2: DietaryRuleEngine data-quality findings")
class DietaryRuleEngineTest {

    private IngredientResolver resolver;
    private DietaryRuleTool dietaryRuleTool;
    private CrossContaminationTool crossContaminationTool;
    private DietaryRuleEngine engine;

    @BeforeEach
    void setUp() {
        resolver = mock(IngredientResolver.class);
        dietaryRuleTool = mock(DietaryRuleTool.class);
        crossContaminationTool = mock(CrossContaminationTool.class);
        engine = new DietaryRuleEngine(
                List.of(), resolver, dietaryRuleTool, crossContaminationTool);

        when(crossContaminationTool.analyse(any(), any()))
                .thenReturn(new CrossContaminationResult(false, List.of(), ""));

        // The engine now resolves the whole label in one batched call; delegate that batch
        // to the per-name resolve() stubs each test already sets up.
        when(resolver.resolveAll(any())).thenAnswer(invocation -> {
            List<String> names = invocation.getArgument(0);
            Map<String, IngredientResolution> resolutions = new LinkedHashMap<>();
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    resolutions.put(name, resolver.resolve(name));
                }
            }
            return resolutions;
        });
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
        verify(dietaryRuleTool, never()).lookup(anyString());
        verify(crossContaminationTool, never()).analyse(any(), any());
    }

    @Test
    @DisplayName("Truly unknown ingredients emit UNRESOLVED findings")
    void unknownIngredientsEmitUnresolvedFindings() {
        Ingredient mystery = new Ingredient("Mystery Powder", null, null, false);
        when(resolver.resolve("Mystery Powder")).thenReturn(IngredientResolution.unknown());

        ProductData product = new ProductData(
                "4901330305840",
                List.of(mystery),
                "Mystery Powder",
                List.of(),
                null,
                true
        );

        SafetyVerdict verdict = engine.assess(List.of(), product);

        assertEquals(SafetyVerdict.Level.WARNING, verdict.level());
        assertEquals(1, verdict.findings().size());
        assertEquals(DietaryRuleEngine.UNRESOLVED, verdict.findings().getFirst().restrictionCode());
        assertEquals("Mystery Powder", verdict.findings().getFirst().ingredientName());
    }

    @Test
    @DisplayName("Known-safe catalog ingredients do not emit UNRESOLVED warnings")
    void knownSafeIngredientsDoNotWarn() {
        Ingredient sugar = new Ingredient("Sugar", null, null, false);
        Ingredient salt = new Ingredient("Salt", null, null, false);
        when(resolver.resolve("Sugar")).thenReturn(IngredientResolution.knownSafe());
        when(resolver.resolve("Salt")).thenReturn(IngredientResolution.knownSafe());

        ProductData product = new ProductData(
                "123",
                List.of(sugar, salt),
                "Sugar, Salt",
                List.of(),
                null,
                true
        );

        SafetyVerdict verdict = engine.assess(List.of(), product);

        assertEquals(SafetyVerdict.Level.SAFE, verdict.level());
        assertTrue(verdict.findings().isEmpty());
    }

    @Test
    @DisplayName("Resolved dairy root stays available for allergen rules")
    void resolvedRootIsAppliedBeforeCheckers() {
        Ingredient milkSolid = new Ingredient("Milk Soild", null, null, false);
        when(resolver.resolve("Milk Soild")).thenReturn(IngredientResolution.resolved("DAIRY"));
        when(dietaryRuleTool.lookup("DAIRY"))
                .thenReturn(new DietaryRuleResult("DAIRY", "ALLERGEN", "Avoid dairy."));

        AllergenChecker allergenChecker = new AllergenChecker();
        DietaryRuleEngine withChecker = new DietaryRuleEngine(
                List.of(allergenChecker), resolver, dietaryRuleTool, crossContaminationTool);

        ProductData product = new ProductData(
                "123",
                List.of(milkSolid),
                "Milk Soild",
                List.of(),
                null,
                true
        );

        SafetyVerdict verdict = withChecker.assess(
                List.of(new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)),
                product
        );

        assertEquals(SafetyVerdict.Level.UNSAFE, verdict.level());
        assertEquals("DAIRY", verdict.findings().getFirst().restrictionCode());
        assertEquals("Milk Soild", verdict.findings().getFirst().ingredientName());
    }

    @Test
    @DisplayName("Dietary-rule tool drops UNKNOWN definitions before checkers run")
    void dropsUnknownDietaryRulesBeforeCheckers() {
        Ingredient milk = new Ingredient("Milk", null, "DAIRY", false);
        when(dietaryRuleTool.lookup("NOT_A_REAL_RULE"))
                .thenReturn(new DietaryRuleResult("NOT_A_REAL_RULE", "UNKNOWN", "missing"));
        when(dietaryRuleTool.lookup("DAIRY"))
                .thenReturn(new DietaryRuleResult("DAIRY", "ALLERGEN", "Avoid dairy."));

        AllergenChecker allergenChecker = new AllergenChecker();
        DietaryRuleEngine withChecker = new DietaryRuleEngine(
                List.of(allergenChecker), resolver, dietaryRuleTool, crossContaminationTool);

        ProductData product = new ProductData(
                "123",
                List.of(milk),
                "Milk",
                List.of(),
                null,
                true
        );

        SafetyVerdict verdict = withChecker.assess(
                List.of(
                        new RestrictionRule("NOT_A_REAL_RULE", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID),
                        new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
                ),
                product
        );

        assertEquals(SafetyVerdict.Level.UNSAFE, verdict.level());
        assertEquals(1, verdict.findings().size());
        assertEquals("DAIRY", verdict.findings().getFirst().restrictionCode());
    }

    @Test
    @DisplayName("Cross-contamination overlap adds WARNING without escalating to UNSAFE")
    void crossContaminationOverlapAddsWarningOnly() {
        Ingredient potato = new Ingredient("Potato", null, null, false);
        when(resolver.resolve("Potato")).thenReturn(IngredientResolution.knownSafe());
        when(dietaryRuleTool.lookup("DAIRY"))
                .thenReturn(new DietaryRuleResult("DAIRY", "ALLERGEN", "Avoid dairy."));
        when(crossContaminationTool.analyse("Potato. May contain milk.", List.of()))
                .thenReturn(new CrossContaminationResult(
                        true,
                        List.of("MILK"),
                        "May contain milk"
                ));

        ProductData product = new ProductData(
                "123",
                List.of(potato),
                "Potato. May contain milk.",
                List.of(),
                null,
                true
        );

        SafetyVerdict verdict = engine.assess(
                List.of(new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)),
                product
        );

        assertEquals(SafetyVerdict.Level.WARNING, verdict.level());
        assertEquals(DietaryRuleEngine.CROSS_CONTAMINATION, verdict.findings().getFirst().restrictionCode());
        assertEquals("DAIRY", verdict.findings().getFirst().ingredientName());
        assertTrue(verdict.findings().getFirst().reason().contains("May contain milk"));
    }

    @Test
    @DisplayName("Traces-only OFF tags add CROSS_CONTAMINATION WARNING")
    void tracesOnlyTagsAddCrossContaminationWarning() {
        Ingredient potato = new Ingredient("Potato", null, null, false);
        when(resolver.resolve("Potato")).thenReturn(IngredientResolution.knownSafe());
        when(dietaryRuleTool.lookup("DAIRY"))
                .thenReturn(new DietaryRuleResult("DAIRY", "ALLERGEN", "Avoid dairy."));
        when(crossContaminationTool.analyse(isNull(), eq(List.of("en:milk"))))
                .thenReturn(new CrossContaminationResult(
                        true,
                        List.of("MILK"),
                        "traces_tags: en:milk"
                ));

        ProductData product = new ProductData(
                "123",
                List.of(potato),
                null,
                List.of(),
                List.of("en:milk"),
                null,
                true
        );

        SafetyVerdict verdict = engine.assess(
                List.of(new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)),
                product
        );

        assertEquals(SafetyVerdict.Level.WARNING, verdict.level());
        assertEquals(DietaryRuleEngine.CROSS_CONTAMINATION, verdict.findings().getFirst().restrictionCode());
        assertTrue(verdict.findings().getFirst().reason().contains("traces_tags"));
    }

    @Test
    @DisplayName("E-number canonical name is applied before checkers")
    void eNumberCanonicalNameIsAppliedBeforeCheckers() {
        Ingredient e1105 = new Ingredient("E1105", null, null, false);
        when(resolver.resolve("E1105")).thenReturn(IngredientResolution.resolved(
                "EGG",
                "E1105 (Lysozyme from eggs)",
                true));
        when(dietaryRuleTool.lookup("EGG"))
                .thenReturn(new DietaryRuleResult("EGG", "ALLERGEN", "Avoid egg."));

        AllergenChecker allergenChecker = new AllergenChecker();
        DietaryRuleEngine withChecker = new DietaryRuleEngine(
                List.of(allergenChecker), resolver, dietaryRuleTool, crossContaminationTool);

        ProductData product = new ProductData(
                "123",
                List.of(e1105),
                "E1105",
                List.of(),
                null,
                true
        );

        SafetyVerdict verdict = withChecker.assess(
                List.of(new RestrictionRule("EGG", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)),
                product
        );

        assertEquals(SafetyVerdict.Level.UNSAFE, verdict.level());
        assertEquals("EGG", verdict.findings().getFirst().restrictionCode());
        assertEquals("E1105 (Lysozyme from eggs)", verdict.findings().getFirst().ingredientName());
    }
}
