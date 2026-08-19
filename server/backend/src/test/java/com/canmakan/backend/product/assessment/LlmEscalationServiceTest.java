package com.canmakan.backend.product.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.ai.llm.LlmClient;
import com.canmakan.backend.ai.llm.PromptBuilder;
import com.canmakan.backend.ai.llm.ResolvedIngredient;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.assessment.dto.TieredOutcome;
import com.canmakan.backend.product.assessment.service.LlmEscalationService;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LlmEscalationService}: the escalation policy and — critically —
 * that the LLM only supplies evidence while the engine decides the verdict (evidence below
 * the confidence threshold is ignored).
 *
 * @author XieHuayuan
 * @author Amelia
 */
@DisplayName("UC3: LlmEscalationService Tier-3 evidence flow")
@ExtendWith(MockitoExtension.class)
class LlmEscalationServiceTest {

    @Mock private PromptBuilder promptBuilder;
    @Mock private LlmClient llmClient;
    @Mock private DietaryRuleEngine ruleEngine;

    @InjectMocks
    private LlmEscalationService service;

    @Test
    @DisplayName("BE1: SAFE is definitive and does not escalate")
    void safeDoesNotEscalate() {
        TieredOutcome outcome = service.escalate(
                List.of(), productWith(true, ingredient("Milk", "DAIRY")),
                SafetyVerdict.safe("ok", List.of()), "123");

        assertEquals(ExecutionTier.TIER_1_RULES, outcome.tier());
        assertEquals(SafetyVerdict.Level.SAFE, outcome.verdict().level());
        verifyNoInteractions(promptBuilder, llmClient, ruleEngine);
    }

    @Test
    @DisplayName("BE2: WARNING on incomplete data does not escalate")
    void warningWithIncompleteDataDoesNotEscalate() {
        TieredOutcome outcome = service.escalate(
                List.of(), productWith(false, ingredient("Casein", null)),
                SafetyVerdict.warning("uncertain", List.of()), "123");

        assertEquals(ExecutionTier.TIER_1_RULES, outcome.tier());
        verifyNoInteractions(promptBuilder, llmClient, ruleEngine);
    }

    @Test
    @DisplayName("BE3: WARNING escalates, gets evidence, and the engine re-decides")
    void warningEscalatesThenEngineReDecides() {
        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        when(llmClient.assess("prompt")).thenReturn(llmResult("Casein", "DAIRY", 0.9));
        when(ruleEngine.assess(any(), any())).thenReturn(SafetyVerdict.unsafe("resolved to dairy", List.of()));

        TieredOutcome outcome = service.escalate(
                List.of(), productWith(true, ingredient("Casein", null)),
                SafetyVerdict.warning("uncertain", List.of()), "123");

        assertEquals(ExecutionTier.TIER_3_LLM, outcome.tier());
        assertEquals(SafetyVerdict.Level.UNSAFE, outcome.verdict().level());
        verify(llmClient).assess("prompt");
        verify(ruleEngine, times(1)).assess(any(), any());
    }

    @Test
    @DisplayName("BE4: High-confidence evidence enriches an UNRESOLVED ingredient before reassessment")
    void highConfidenceEvidenceEnrichesTheProduct() {
        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        when(llmClient.assess("prompt")).thenReturn(llmResult("Casein", "DAIRY", 0.9));
        when(ruleEngine.assess(any(), any())).thenReturn(SafetyVerdict.unsafe("resolved", List.of()));

        service.escalate(List.of(), productWith(true, ingredient("Casein", null)),
                warningUnresolved("Casein"), "123");

        assertEquals("DAIRY", captureReassessedProduct().ingredients().get(0).rootAllergen());
    }

    @Test
    @DisplayName("BE5: Low-confidence evidence is ignored and cannot force a verdict")
    void lowConfidenceEvidenceIsIgnored() {
        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        when(llmClient.assess("prompt")).thenReturn(llmResult("Casein", "DAIRY", 0.5)); // below 0.7
        when(ruleEngine.assess(any(), any())).thenReturn(SafetyVerdict.warning("still uncertain", List.of()));

        service.escalate(List.of(), productWith(true, ingredient("Casein", null)),
                warningUnresolved("Casein"), "123");

        assertNull(captureReassessedProduct().ingredients().get(0).rootAllergen()); // NOT enriched
    }

    @Test
    @DisplayName("BE7: LLM evidence never overwrites an ingredient the engine already resolved as safe")
    void llmEvidenceDoesNotTagResolvedIngredients() {
        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        // The LLM wrongly claims Water is DAIRY at high confidence.
        when(llmClient.assess("prompt")).thenReturn(llmResult("Water", "DAIRY", 0.9));
        when(ruleEngine.assess(any(), any())).thenReturn(SafetyVerdict.warning("still uncertain", List.of()));

        // Water is not in the Tier-1 UNRESOLVED set (only Casein is), so it must stay untouched.
        service.escalate(List.of(),
                productWith(true, ingredient("Water", null), ingredient("Casein", null)),
                warningUnresolved("Casein"), "123");

        List<Ingredient> reassessed = captureReassessedProduct().ingredients();
        assertNull(reassessed.get(0).rootAllergen()); // Water stays safe despite the LLM guess
    }

    @Test
    @DisplayName("BE6: WARNING escalation falls back to rules tier when AI is disabled")
    void escalationFallsBackWhenAiDisabled() {
        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        when(llmClient.assess("prompt")).thenThrow(new IllegalStateException("AI assessment is disabled."));

        TieredOutcome outcome = service.escalate(
                List.of(), productWith(true, ingredient("Casein", null)),
                SafetyVerdict.warning("uncertain", List.of()), "123");

        assertEquals(ExecutionTier.TIER_1_RULES, outcome.tier());
        assertEquals(SafetyVerdict.Level.WARNING, outcome.verdict().level());
        verifyNoInteractions(ruleEngine);
    }

    // --- helpers -----------------------------------------------------------------

    private ProductData captureReassessedProduct() {
        ArgumentCaptor<ProductData> captor = ArgumentCaptor.forClass(ProductData.class);
        verify(ruleEngine).assess(any(), captor.capture());
        return captor.getValue();
    }

    private static ProductData productWith(boolean dataComplete, Ingredient... ingredients) {
        return new ProductData("123", List.of(ingredients), "text", List.of(), null, dataComplete);
    }

    private static Ingredient ingredient(String name, String rootAllergen) {
        return new Ingredient(name, null, rootAllergen, false);
    }

    /** A Tier-1 WARNING that carries the grouped UNRESOLVED finding naming the given ingredients. */
    private static SafetyVerdict warningUnresolved(String... unresolvedNames) {
        Finding unresolved = new Finding(
                DietaryRuleEngine.UNRESOLVED,
                String.join(", ", unresolvedNames),
                "Treat these ingredients with caution: " + String.join(", ", unresolvedNames) + ".");
        return SafetyVerdict.warning("uncertain", List.of(unresolved));
    }

    private static LlmAssessmentResult llmResult(String ingredient, String rootAllergen, double confidence) {
        return new LlmAssessmentResult(
            List.of(new ResolvedIngredient(ingredient, rootAllergen, confidence)),
            "evidence notes",
            "gpt-4o", 10, 5, 20L, "prompt", "response");
    }
}
