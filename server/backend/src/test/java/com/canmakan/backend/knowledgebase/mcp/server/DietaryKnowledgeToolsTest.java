package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.knowledgebase.mcp.contract.AllergenRelationshipResult;
import com.canmakan.backend.knowledgebase.mcp.contract.CrossContaminationResult;
import com.canmakan.backend.knowledgebase.mcp.contract.DietaryRuleResult;
import com.canmakan.backend.knowledgebase.mcp.contract.ENumberResult;
import com.canmakan.backend.knowledgebase.mcp.contract.IngredientAliasResult;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
import com.canmakan.backend.knowledgebase.repository.IngredientEntity;
import com.canmakan.backend.knowledgebase.repository.IngredientEntityRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/*
 * Unit tests for repository functions on Knowledge Base
 * 
 * @author Amelia
 */
@DisplayName("UC3: DietaryKnowledgeTools - Repository functions on Knowledge Base")
class DietaryKnowledgeToolsTest {

    /** Seed rows aligned with {@code 05_household_dietary_data.sql}. */
    private static final Map<String, DietaryRestriction> SEED_RESTRICTIONS = seedRestrictions();

    /** Mock repositories for testing. */
    private final IngredientEntityRepository ingredientEntityRepository = mock(IngredientEntityRepository.class);
    private final DietaryRestrictionRepository dietaryRestrictionRepository =
            mock(DietaryRestrictionRepository.class);
    private final DietaryKnowledgeRepository repository =
            new DietaryKnowledgeRepository(ingredientEntityRepository, dietaryRestrictionRepository);
    private final IngredientAliasTool ingredientAliasTool = new IngredientAliasTool(repository);
    private final ENumberTool eNumberTool = new ENumberTool(repository);
    private final AllergenRelationshipLookupFallback realFallback = new AllergenRelationshipLookupFallback(
        null,
        "local-dev-placeholder",
        "https://api.tavily.com/search");
    private final AllergenRelationshipTool allergenRelationshipTool =
        new AllergenRelationshipTool(repository, realFallback);
    private final DietaryRuleTool dietaryRuleTool = new DietaryRuleTool(repository);
    private final CrossContaminationTool crossContaminationTool = new CrossContaminationTool(repository);

    /** Set up the mock repositories and tools. */
    @BeforeEach
    void setUp() {
        when(ingredientEntityRepository.findAll()).thenReturn(List.of(
                new IngredientEntity("milk powder", null, "DAIRY", false),
                new IngredientEntity("milk", null, "DAIRY", false),
                new IngredientEntity("whey", "Milk", "DAIRY", false),
                new IngredientEntity("peanut", "Peanuts", "PEANUT", false),
                new IngredientEntity("sesame", null, "SESAME", false),
                new IngredientEntity("Whole Grain Oat Flour", null, "GLUTEN", false),
                new IngredientEntity("Sodium Caseinate", "Milk Derivatives", "DAIRY", false),
                new IngredientEntity("E471 (Mono- and Diglycerides)", "Emulsifiers", "ADDITIVE", true),
                new IngredientEntity("E1105 (Lysozyme from eggs)", "Egg Derivatives", "EGG", true)
        ));

        // Seed-aligned chemical aliases from 02_ingredients.sql
        when(ingredientEntityRepository.findByIngredientNameContainingIgnoreCase(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            if (query == null) {
                return List.of();
            }
            String q = query.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
            List<IngredientEntity> chemicalAliases = List.of(
                new IngredientEntity("E471 (Mono- and Diglycerides)", "Emulsifiers", "ADDITIVE", true),
                new IngredientEntity("E473 (Sucrose Esters of Fatty Acids)", "Emulsifiers", "ADDITIVE", true),
                new IngredientEntity("E1105 (Lysozyme from eggs)", "Egg Derivatives", "EGG", true)
            );
            return chemicalAliases.stream()
                .filter(entity -> entity.getIngredientName().toUpperCase(Locale.ROOT).contains(q))
                .toList();
        });

        // Mirrors DietaryRestrictionRepository.findByCodeIgnoreCase (case-insensitive)
        when(dietaryRestrictionRepository.findByCodeIgnoreCase(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            if (code == null || code.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(SEED_RESTRICTIONS.get(code.trim().toLowerCase(Locale.ROOT)));
        });

        repository.initialize();
    }

    /** Test cases for the ingredient alias tool. */
    @Test
    @DisplayName("UC3 BE1a: Resolves ingredient aliases and root allergen")
    void resolvesIngredientAliasesAndRootAllergen() {
        IngredientAliasResult result = ingredientAliasTool.lookup("milk powder");

        assertThat(result.ingredientName()).isEqualTo("milk powder");
        assertThat(result.canonicalName()).isEqualTo("milk powder");
        assertThat(result.rootAllergen()).isEqualTo("DAIRY");
        assertThat(result.chemicalAlias()).isFalse();
        assertThat(result.matched()).isTrue();
    }

    @Test
    @DisplayName("UC3 BE1b: Blank ingredient alias returns empty unresolved result")
    void blankIngredientAliasReturnsEmptyUnresolvedResult() {
        IngredientAliasResult result = ingredientAliasTool.lookup("  ");

        assertThat(result.ingredientName()).isEmpty();
        assertThat(result.canonicalName()).isEmpty();
        assertThat(result.rootAllergen()).isNull();
        assertThat(result.chemicalAlias()).isFalse();
        assertThat(result.matched()).isFalse();
    }

    @Test
    @DisplayName("UC3 BE1c: Unknown ingredient keeps passthrough canonical and null root")
    void unknownIngredientKeepsPassthroughCanonicalAndNullRoot() {
        IngredientAliasResult result = ingredientAliasTool.lookup("mystery powder");

        assertThat(result.ingredientName()).isEqualTo("mystery powder");
        assertThat(result.canonicalName()).isEqualTo("mystery powder");
        assertThat(result.rootAllergen()).isNull();
        assertThat(result.chemicalAlias()).isFalse();
        assertThat(result.matched()).isFalse();
    }

    @Test
    @DisplayName("UC3 BE1d: Trims and case-normalizes catalog lookup")
    void trimsAndCaseNormalizesCatalogLookup() {
        IngredientAliasResult result = ingredientAliasTool.lookup("  Milk Powder  ");

        assertThat(result.ingredientName()).isEqualTo("Milk Powder");
        assertThat(result.canonicalName()).isEqualTo("milk powder");
        assertThat(result.rootAllergen()).isEqualTo("DAIRY");
    }

    @Test
    @DisplayName("UC3 BE1e: Resolves common synonym to canonical catalog row")
    void resolvesCommonSynonymToCanonicalCatalogRow() {
        IngredientAliasResult result = ingredientAliasTool.lookup("caseinate");

        assertThat(result.canonicalName()).isEqualTo("Sodium Caseinate");
        assertThat(result.rootAllergen()).isEqualTo("DAIRY");
        assertThat(result.chemicalAlias()).isFalse();
    }

    @Test
    @DisplayName("UC3 BE1f: Chemical full name sets chemicalAlias and ADDITIVE root")
    void chemicalFullNameSetsChemicalAliasAndAdditiveRoot() {
        IngredientAliasResult result =
                ingredientAliasTool.lookup("E471 (Mono- and Diglycerides)");

        assertThat(result.canonicalName()).isEqualTo("E471 (Mono- and Diglycerides)");
        assertThat(result.rootAllergen()).isEqualTo("ADDITIVE");
        assertThat(result.chemicalAlias()).isTrue();
    }

    @Test
    @DisplayName("UC3 BE1g: Bare E-code resolves via alias map for client chemical path")
    void bareECodeResolvesViaAliasMap() {
        IngredientAliasResult result = ingredientAliasTool.lookup("e471");

        assertThat(result.canonicalName()).isEqualTo("E471 (Mono- and Diglycerides)");
        assertThat(result.rootAllergen()).isEqualTo("ADDITIVE");
        assertThat(result.chemicalAlias()).isTrue();
    }

    @Test
    @DisplayName("UC3 BE1h: Oat flour synonym resolves gluten root")
    void oatFlourSynonymResolvesGlutenRoot() {
        IngredientAliasResult result = ingredientAliasTool.lookup("oat flour");

        assertThat(result.canonicalName()).isEqualTo("Whole Grain Oat Flour");
        assertThat(result.rootAllergen()).isEqualTo("GLUTEN");
    }

    /** Test cases for the E-number tool. */
    @Test
    @DisplayName("UC3 BE2a: Resolves E-number metadata for E471")
    void resolvesENumberMetadata() {
        ENumberResult result = eNumberTool.lookup("E471");

        assertThat(result.eNumber()).isEqualTo("E471");
        assertThat(result.name()).contains("Diglycerides");
        assertThat(result.category()).isEqualTo("Emulsifiers");
        assertThat(result.rootAllergen()).isEqualTo("ADDITIVE");
        assertThat(result.animalDerived()).isFalse();
    }

    @Test
    @DisplayName("UC3 BE2b: Blank E-number returns empty result")
    void blankENumberReturnsEmptyResult() {
        ENumberResult result = eNumberTool.lookup("  ");

        assertThat(result.eNumber()).isEmpty();
        assertThat(result.name()).isEmpty();
        assertThat(result.category()).isEmpty();
        assertThat(result.animalDerived()).isFalse();
    }

    @Test
    @DisplayName("UC3 BE2c: Unknown E-number returns Unknown additive")
    void unknownENumberReturnsUnknownAdditive() {
        ENumberResult result = eNumberTool.lookup("E999");

        assertThat(result.eNumber()).isEqualTo("E999");
        assertThat(result.name()).isEqualTo("Unknown additive");
        assertThat(result.category()).isEqualTo("unknown");
        assertThat(result.animalDerived()).isFalse();
    }

    @Test
    @DisplayName("UC3 BE2d: E-number lookup normalizes case and separators")
    void eNumberLookupNormalizesCaseAndSeparators() {
        ENumberResult result = eNumberTool.lookup("e-471");

        assertThat(result.eNumber()).isEqualTo("E471");
        assertThat(result.name()).contains("Diglycerides");
    }

    @Test
    @DisplayName("UC3 BE2e: Partial E-number does not match a longer code")
    void partialENumberDoesNotMatchLongerCode() {
        ENumberResult result = eNumberTool.lookup("E47");

        assertThat(result.name()).isEqualTo("Unknown additive");
        assertThat(result.category()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("UC3 BE2f: E1105 from eggs is flagged animal-derived")
    void e1105FromEggsIsAnimalDerived() {
        ENumberResult result = eNumberTool.lookup("E1105");

        assertThat(result.eNumber()).isEqualTo("E1105");
        assertThat(result.name()).contains("Lysozyme");
        assertThat(result.category()).isEqualTo("Egg Derivatives");
        assertThat(result.rootAllergen()).isEqualTo("EGG");
        assertThat(result.animalDerived()).isTrue();
    }

    /** Test cases for the allergen relationship tool. */
    @Test
    @DisplayName("UC3 BE3: Resolves allergen hierarchy")
    void resolvesAllergenHierarchy() {
        AllergenRelationshipResult result = allergenRelationshipTool.lookup(List.of("Whey"));

        assertThat(result.localMatches()).hasSize(1);
        assertThat(result.localMatches().get(0).parentAllergen()).isEqualTo("Milk");
        assertThat(result.localMatches().get(0).rootAllergen()).isEqualTo("DAIRY");
        assertThat(result.unresolvedIngredients()).isEmpty();
        assertThat(result.externalSearchSummary()).isEmpty();
        assertThat(result.externalMatches()).isEmpty();
    }

    @Test
    @DisplayName("UC3 BE4: Batches unresolved ingredients for fallback lookup")
    void batchesMultipleIngredientsForFallbackLookup() {
        RecordingFallback fallback = new RecordingFallback();
        AllergenRelationshipTool tool = new AllergenRelationshipTool(repository, fallback);

        tool.lookup(List.of("whey", "peanut", "sesame"));

        assertThat(fallback.lastIngredients()).containsExactly("sesame");
    }

    @Test
    @DisplayName("UC3 BE5: Loads allergen knowledge from SQL test data")
    void loadsAllergenKnowledgeFromSqlTestData() {
        IngredientAliasResult result = ingredientAliasTool.lookup("Whole Grain Oat Flour");

        assertThat(result.canonicalName()).isEqualTo("Whole Grain Oat Flour");
        assertThat(result.rootAllergen()).isEqualTo("GLUTEN");
    }

    @Test
    @DisplayName("UC3 BE6: Applies local hierarchy to domain ingredients")
    void appliesLocalHierarchyToDomainIngredients() {
        AllergenRelationshipResult result = allergenRelationshipTool.lookup(List.of("whey"));
        List<Ingredient> enriched = allergenRelationshipTool.applyHierarchy(
            List.of(new Ingredient("whey", null, null, false)), result);

        assertThat(enriched).hasSize(1);
        assertThat(enriched.get(0).rootAllergen()).isEqualTo("DAIRY");
        assertThat(enriched.get(0).parentAllergen()).isEqualTo("Milk");
    }

    /** Test cases for the dietary rule tool. */
    @Test
    @DisplayName("UC3 BE6a: Resolves dietary rules by seed code HALAL")
    void resolvesDietaryRulesByCode() {
        DietaryRuleResult result = dietaryRuleTool.lookup("HALAL");

        assertThat(result.code()).isEqualTo("HALAL");
        assertThat(result.category()).isEqualTo("RELIGIOUS");
        assertThat(result.description()).isEqualTo(
            "Requires Halal-certified ingredients and no pork or alcohol.");
    }

    @Test
    @DisplayName("UC3 BE6b: Blank dietary rule code returns empty result")
    void blankDietaryRuleCodeReturnsEmptyResult() {
        DietaryRuleResult result = dietaryRuleTool.lookup("  ");

        assertThat(result.code()).isEmpty();
        assertThat(result.category()).isEmpty();
        assertThat(result.description()).isEmpty();
    }

    @Test
    @DisplayName("UC3 BE6c: Unknown dietary rule code returns UNKNOWN")
    void unknownDietaryRuleCodeReturnsUnknown() {
        DietaryRuleResult result = dietaryRuleTool.lookup("NOT_A_REAL_RULE");

        assertThat(result.code()).isEqualTo("NOT_A_REAL_RULE");
        assertThat(result.category()).isEqualTo("UNKNOWN");
        assertThat(result.description()).contains("No dietary rule definition found");
    }

    @Test
    @DisplayName("UC3 BE6d: Dietary rule lookup is case-insensitive for LOW_SUGAR")
    void dietaryRuleLookupIsCaseInsensitive() {
        DietaryRuleResult result = dietaryRuleTool.lookup("low_sugar");

        assertThat(result.code()).isEqualTo("LOW_SUGAR");
        assertThat(result.category()).isEqualTo("DIET");
        assertThat(result.description()).isEqualTo("Checks sugar per 100 g");
    }

    /** Test cases for the cross contamination tool. */
    @Test
    @DisplayName("UC3 BE7a: Detects cross-contamination phrases")
    void detectsCrossContaminationPhrases() {
        CrossContaminationResult result =
            crossContaminationTool.analyse("May contain nuts and produced in a facility with milk");

        assertThat(result.mayContain()).isTrue();
        assertThat(result.allergens()).contains("NUTS", "MILK", "DAIRY");
        assertThat(result.phrase()).contains("May contain");
    }

    @Test
    @DisplayName("UC3 BE7b: Blank label and traces return empty result")
    void blankLabelAndTracesReturnEmptyResult() {
        CrossContaminationResult result = crossContaminationTool.analyse("  ", null);

        assertThat(result.mayContain()).isFalse();
        assertThat(result.allergens()).isEmpty();
        assertThat(result.phrase()).isEmpty();
    }

    @Test
    @DisplayName("UC3 BE7c: No cross-contamination phrase yields empty result")
    void noCrossContaminationPhraseYieldsEmptyResult() {
        CrossContaminationResult result =
            crossContaminationTool.analyse("Ingredients: water, sugar, salt");

        assertThat(result.mayContain()).isFalse();
        assertThat(result.allergens()).isEmpty();
    }

    @Test
    @DisplayName("UC3 BE7d: Uses Open Food Facts traces_tags")
    void usesOpenFoodFactsTracesTags() {
        CrossContaminationResult result =
            crossContaminationTool.analyse(null, List.of("en:milk", "en:nuts"));

        assertThat(result.mayContain()).isTrue();
        assertThat(result.allergens()).contains("MILK", "DAIRY", "NUTS");
        assertThat(result.phrase()).startsWith("traces_tags:");
    }

    @Test
    @DisplayName("UC3 BE7e: Ignores nutrition/eggplant false positives")
    void ignoresNutritionAndEggplantFalsePositives() {
        CrossContaminationResult result = crossContaminationTool.analyse(
            "May contain traces. Nutrition information. Contains eggplant extract.");

        assertThat(result.allergens()).doesNotContain("NUTS", "EGG");
    }

    /** Test cases for the allergen relationship tool. */
    @Test
    @DisplayName("UC3 BE8: Deduplicates case-insensitive ingredient inputs")
    void deduplicatesCaseInsensitiveIngredientInputs() {
        RecordingFallback fallback = new RecordingFallback();
        AllergenRelationshipTool tool = new AllergenRelationshipTool(repository, fallback);

        AllergenRelationshipResult result = tool.lookup(List.of("whey", "Whey", "unknown", "Unknown"));

        assertThat(result.localMatches()).hasSize(1);
        assertThat(result.unresolvedIngredients()).containsExactly("unknown");
        assertThat(fallback.lastIngredients()).containsExactly("unknown");
    }

    @Test
    @DisplayName("UC3 BE9: Placeholder Tavily key yields empty summary without crash")
    void placeholderTavilyKeyYieldsEmptySummaryWithoutCrash() {
        AllergenRelationshipLookupFallback fallback = new AllergenRelationshipLookupFallback(
            null,
            "local-dev-placeholder",
            "https://api.tavily.com/search");
        AllergenRelationshipTool tool = new AllergenRelationshipTool(repository, fallback);

        AllergenRelationshipResult result = tool.lookup(List.of("mystery-additive"));

        assertThat(result.unresolvedIngredients()).containsExactly("mystery-additive");
        assertThat(result.externalSearchSummary()).isEmpty();
        assertThat(result.externalMatches()).isEmpty();
    }

    @Test
    @DisplayName("UC3 BE10: Tavily fallback uses WebClient response answer")
    void tavilyFallbackUsesWebClientResponseAnswer() {
        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"answer\":\"Inulin -> NONE\",\"results\":[]}")
                    .build());

        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        AllergenRelationshipLookupFallback fallback = new AllergenRelationshipLookupFallback(
                builder,
                "tvly-test-key",
                "https://api.tavily.com/search");

        String summary = fallback.searchExternal(List.of("inulin"));

        assertThat(summary).contains("Inulin -> NONE");
    }

    @Test
    @DisplayName("UC3 BE10b: Unresolved Tavily answer is parsed into externalMatches")
    void unresolvedTavilyAnswerIsParsedIntoExternalMatches() {
        AllergenRelationshipLookupFallback fallback = new AllergenRelationshipLookupFallback(
                null,
                "local-dev-placeholder",
                "https://api.tavily.com/search") {
            @Override
            public String searchExternal(List<String> ingredients) {
                return "Casein -> DAIRY\nMystery Fiber -> NONE";
            }
        };
        AllergenRelationshipTool tool = new AllergenRelationshipTool(repository, fallback);

        AllergenRelationshipResult result = tool.lookup(List.of("Casein", "Mystery Fiber"));

        assertThat(result.unresolvedIngredients()).containsExactly("Casein", "Mystery Fiber");
        assertThat(result.externalMatches()).hasSize(2);
        assertThat(result.externalMatches().get(0).rootAllergen()).isEqualTo("DAIRY");
        assertThat(result.externalMatches().get(1).rootAllergen()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("UC3 BE11: Null WebClient builder with real key skips safely")
    void nullWebClientBuilderWithRealKeySkipsSafely() {
        AllergenRelationshipLookupFallback fallback = new AllergenRelationshipLookupFallback(
                null,
                "tvly-real-looking-key",
                "https://api.tavily.com/search");

        assertThat(fallback.searchExternal(List.of("inulin"))).isEmpty();
    }

    private static Map<String, DietaryRestriction> seedRestrictions() {
        Map<String, DietaryRestriction> byCode = new LinkedHashMap<>();
        addRestriction(byCode, 1L, "GLUTEN", "Gluten Free", "ALLERGEN",
                "Strictly avoid wheat, barley, rye, and oat gluten.");
        addRestriction(byCode, 2L, "DAIRY", "Lactose / Dairy Intolerance", "ALLERGEN",
                "Avoid milk solids, lactose, whey, and dairy fats.");
        addRestriction(byCode, 8L, "HALAL", "Halal Diet", "RELIGIOUS",
                "Requires Halal-certified ingredients and no pork or alcohol.");
        addRestriction(byCode, 11L, "LOW_SUGAR", "Low Sugar", "DIET",
                "Checks sugar per 100 g");
        return byCode;
    }

    private static void addRestriction(
            Map<String, DietaryRestriction> byCode,
            long id,
            String code,
            String displayName,
            String category,
            String description) {
        DietaryRestriction restriction = new DietaryRestriction();
        restriction.setId(id);
        restriction.setCode(code);
        restriction.setDisplayName(displayName);
        restriction.setCategory(category);
        restriction.setDescription(description);
        byCode.put(code.toLowerCase(Locale.ROOT), restriction);
    }

    private static final class RecordingFallback extends AllergenRelationshipLookupFallback {
        private List<String> lastIngredients;

        RecordingFallback() {
            super(null, "local-dev-placeholder", "https://api.tavily.com/search");
        }

        @Override
        public String searchExternal(List<String> ingredients) {
            this.lastIngredients = ingredients;
            return "fallback";
        }

        public List<String> lastIngredients() {
            return lastIngredients;
        }
    }

}
