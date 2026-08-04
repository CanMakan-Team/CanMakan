package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.dietaryprofile.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.DietaryRestriction;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DietaryKnowledgeToolsTest {

    private final IngredientEntityRepository ingredientEntityRepository = mock(IngredientEntityRepository.class);
    private final DietaryProfileRepository dietaryProfileRepository = mock(DietaryProfileRepository.class);
    private final DietaryKnowledgeRepository repository =
            new DietaryKnowledgeRepository(ingredientEntityRepository, dietaryProfileRepository);
    private final IngredientAliasTool ingredientAliasTool = new IngredientAliasTool(repository);
    private final ENumberTool eNumberTool = new ENumberTool(repository);
    private final AllergenRelationshipTool allergenRelationshipTool =
            new AllergenRelationshipTool(repository, new RecordingFallback());
    private final DietaryRuleTool dietaryRuleTool = new DietaryRuleTool(repository);
    private final CrossContaminationTool crossContaminationTool = new CrossContaminationTool(repository);

    @BeforeEach
    void setUp() {
        when(ingredientEntityRepository.findAll()).thenReturn(List.of(
                new IngredientEntity("milk powder", null, "DAIRY", false),
                new IngredientEntity("milk", null, "DAIRY", false),
                new IngredientEntity("whey", "Milk", "DAIRY", false),
                new IngredientEntity("peanut", "Peanuts", "PEANUT", false),
                new IngredientEntity("sesame", null, "SESAME", false),
                new IngredientEntity("Whole Grain Oat Flour", null, "GLUTEN", false)
        ));
        when(ingredientEntityRepository.findByIngredientNameContainingIgnoreCase("E471")).thenReturn(List.of(
                new IngredientEntity("E471 (Mono- and Diglycerides)", "Emulsifiers", "ADDITIVE", true)
        ));

        DietaryRestriction halal = new DietaryRestriction();
        halal.setCode("HALAL");
        halal.setDisplayName("Halal Diet");
        halal.setCategory("RELIGIOUS");
        halal.setDescription("Requires Halal-certified ingredients and no pork or alcohol.");
        when(dietaryProfileRepository.findRestrictionByCode("HALAL")).thenReturn(java.util.Optional.of(halal));

        repository.initialize();
    }

    @Test
    @DisplayName("UC3 BE1: Resolves ingredient aliases and root allergen")
    void resolvesIngredientAliasesAndRootAllergen() {
        IngredientAliasResult result = ingredientAliasTool.lookup("milk powder");

        assertThat(result.canonicalName()).isEqualTo("milk powder");
        assertThat(result.rootAllergen()).isEqualTo("DAIRY");
        assertThat(result.chemicalAlias()).isFalse();
    }

    @Test
    @DisplayName("UC3 BE2: Resolves E-number metadata")
    void resolvesENumberMetadata() {
        ENumberResult result = eNumberTool.lookup("E471");

        assertThat(result.name()).contains("Diglycerides");
        assertThat(result.category()).isEqualTo("Emulsifiers");
        assertThat(result.animalDerived()).isFalse();
    }

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

    @Test
    @DisplayName("UC3 BE6: Resolves dietary rules by code")
    void resolvesDietaryRulesByCode() {
        DietaryRuleResult result = dietaryRuleTool.lookup("HALAL");

        assertThat(result.category()).isEqualTo("RELIGIOUS");
        assertThat(result.description()).containsIgnoringCase("halal");
    }

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
                        .body("{\"answer\":\"Inulin is typically a FIBER allergen family.\",\"results\":[]}")
                        .build());

        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        AllergenRelationshipLookupFallback fallback = new AllergenRelationshipLookupFallback(
                builder,
                "tvly-test-key",
                "https://api.tavily.com/search");

        String summary = fallback.searchExternal(List.of("inulin"));

        assertThat(summary).contains("FIBER");
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
