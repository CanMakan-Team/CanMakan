package com.canmakan.backend.product.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.ai.llm.LlmClient;
import com.canmakan.backend.ai.llm.PromptBuilder;
import com.canmakan.backend.ai.llm.ResolvedIngredient;
import com.canmakan.backend.ai.log.AiExecutionLogService;
import com.canmakan.backend.dietaryprofile.RestrictionRuleLoader;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanService;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
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
 * Unit tests for {@link AssessmentOrchestrator}: the tiered flow, escalation policy,
 * and — critically — that the LLM only supplies evidence while the engine decides the
 * verdict (evidence below the confidence threshold is ignored).
 *
 * @author XieHuayuan
 */
@DisplayName("UC3: AssessmentOrchestrator tiered assess flow")
@ExtendWith(MockitoExtension.class)
class AssessmentOrchestratorTest {

    @Mock private ProductDataAdapter productDataAdapter;
    @Mock private RestrictionRuleLoader ruleLoader;
    @Mock private DietaryRuleEngine ruleEngine;
    @Mock private PromptBuilder promptBuilder;
    @Mock private LlmClient llmClient;
    @Mock private ScanService scanService;
    @Mock private AiExecutionLogService aiExecutionLogService;

    @InjectMocks
    private AssessmentOrchestrator orchestrator;

    private static final AssessmentRequest REQUEST = new AssessmentRequest("123", 1L);
    private static final List<RestrictionRule> RULES =
            List.of(new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE));

    @Test
    @DisplayName("UC3 BE1: SAFE stays on rules tier and does not escalate to LLM")
    void safeVerdictStaysTier1AndDoesNotEscalate() {
        stubLoadAndProduct(productWith(true, ingredient("Milk", "DAIRY")));
        when(ruleEngine.assess(any(), any())).thenReturn(SafetyVerdict.safe("ok", List.of()));
        when(scanService.record(any(), any(), any(), any())).thenReturn(scan(100L));

        AssessmentResponse response = orchestrator.assess(7L, REQUEST);

        assertEquals("SAFE", response.verdict());
        assertEquals(ExecutionTier.TIER_1_RULES, response.tier());
        assertEquals(100L, response.scanId());
        verify(ruleEngine, times(1)).assess(any(), any());
        verifyNoInteractions(promptBuilder, llmClient);
        verify(aiExecutionLogService).recordRulesOnly(eq(100L), anyLong());
        verify(aiExecutionLogService, never()).record(anyLong(), any(), any());
    }

    @Test
    @DisplayName("UC3 BE2: UNSAFE is definitive and does not escalate to LLM")
    void unsafeVerdictIsDefinitiveAndDoesNotEscalate() {
        stubLoadAndProduct(productWith(true, ingredient("Peanut", "PEANUT")));
        when(ruleEngine.assess(any(), any())).thenReturn(SafetyVerdict.unsafe("contains peanut", List.of()));
        when(scanService.record(any(), any(), any(), any())).thenReturn(scan(100L));

        AssessmentResponse response = orchestrator.assess(7L, REQUEST);

        assertEquals("UNSAFE", response.verdict());
        assertEquals(ExecutionTier.TIER_1_RULES, response.tier());
        verifyNoInteractions(promptBuilder, llmClient);
    }

    @Test
    @DisplayName("UC3 BE3: WARNING escalates to LLM then engine re-decides verdict")
    void warningEscalatesToLlmThenEngineReDecides() {
        stubLoadAndProduct(productWith(true, ingredient("Casein", null)));
        when(ruleEngine.assess(any(), any()))
                .thenReturn(SafetyVerdict.warning("uncertain", List.of()),
                            SafetyVerdict.unsafe("resolved to dairy", List.of()));
        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        when(llmClient.assess("prompt")).thenReturn(llmResult("Casein", "DAIRY", 0.9));
        when(scanService.record(any(), any(), any(), any())).thenReturn(scan(100L));

        AssessmentResponse response = orchestrator.assess(7L, REQUEST);

        assertEquals("UNSAFE", response.verdict());          // final verdict comes from the engine
        assertEquals(ExecutionTier.TIER_3_LLM, response.tier());
        verify(ruleEngine, times(2)).assess(any(), any());
        verify(llmClient).assess("prompt");
        verify(aiExecutionLogService).record(eq(100L), eq(ExecutionTier.TIER_3_LLM), any(LlmAssessmentResult.class));
        verify(aiExecutionLogService, never()).recordRulesOnly(anyLong(), anyLong());
    }

    @Test
    @DisplayName("UC3 BE4: High-confidence LLM evidence enriches product before reassessment")
    void highConfidenceEvidenceEnrichesTheProductBeforeReassessment() {
        stubLoadAndProduct(productWith(true, ingredient("Casein", null)));
        when(ruleEngine.assess(any(), any()))
                .thenReturn(SafetyVerdict.warning("uncertain", List.of()),
                            SafetyVerdict.unsafe("resolved", List.of()));
        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        when(llmClient.assess("prompt")).thenReturn(llmResult("Casein", "DAIRY", 0.9));
        when(scanService.record(any(), any(), any(), any())).thenReturn(scan(100L));

        orchestrator.assess(7L, REQUEST);

        ProductData reassessed = captureSecondEngineInput();
        assertEquals("DAIRY", reassessed.ingredients().get(0).rootAllergen());
    }

    @Test
    @DisplayName("UC3 BE5: Low-confidence LLM evidence is ignored and cannot force a verdict")
    void lowConfidenceEvidenceIsIgnoredSoTheLlmCannotForceAVerdict() {
        stubLoadAndProduct(productWith(true, ingredient("Casein", null)));
        when(ruleEngine.assess(any(), any()))
                .thenReturn(SafetyVerdict.warning("uncertain", List.of()),
                            SafetyVerdict.warning("still uncertain", List.of()));
        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        when(llmClient.assess("prompt")).thenReturn(llmResult("Casein", "DAIRY", 0.5)); // below 0.7
        when(scanService.record(any(), any(), any(), any())).thenReturn(scan(100L));

        orchestrator.assess(7L, REQUEST);

        ProductData reassessed = captureSecondEngineInput();
        assertNull(reassessed.ingredients().get(0).rootAllergen()); // NOT enriched
    }

    @Test
    @DisplayName("UC3 BE6: WARNING escalation falls back to rules tier when AI is disabled")
    void warningEscalationFallsBackWhenAiDisabled() {
        stubLoadAndProduct(productWith(true, ingredient("Casein", null)));
        when(ruleEngine.assess(any(), any())).thenReturn(SafetyVerdict.warning("uncertain", List.of()));
        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        when(llmClient.assess("prompt")).thenThrow(new IllegalStateException("AI assessment is disabled."));
        when(scanService.record(any(), any(), any(), any())).thenReturn(scan(100L));

        AssessmentResponse response = orchestrator.assess(7L, REQUEST);

        assertEquals("WARNING", response.verdict());
        assertEquals(ExecutionTier.TIER_1_RULES, response.tier());
        assertEquals("Test Product", response.productName());
        assertEquals("123", response.barcode());
        verify(aiExecutionLogService).recordRulesOnly(eq(100L), anyLong());
        verify(aiExecutionLogService, never()).record(anyLong(), any(), any());
    }

    // --- helpers -----------------------------------------------------------------

    private void stubLoadAndProduct(ProductData product) {
        ProductLookupResult lookup = new ProductLookupResult(
                "123",
                "Test Product",
                "food",
                product.ingredients(),
                product.ingredientsText(),
                null,
                product.nutrition(),
                product.dataComplete()
        );
        when(ruleLoader.load(1L)).thenReturn(RULES);
        when(productDataAdapter.lookup("123")).thenReturn(lookup);
        when(productDataAdapter.toProductData(lookup)).thenReturn(product);
    }

    private ProductData captureSecondEngineInput() {
        ArgumentCaptor<ProductData> captor = ArgumentCaptor.forClass(ProductData.class);
        verify(ruleEngine, times(2)).assess(any(), captor.capture());
        return captor.getAllValues().get(1);
    }

    private static ProductData productWith(boolean dataComplete, Ingredient... ingredients) {
        return new ProductData("123", List.of(ingredients), "text", List.of(), null, dataComplete);
    }

    private static Ingredient ingredient(String name, String rootAllergen) {
        return new Ingredient(name, null, rootAllergen, false);
    }

    private static Scan scan(long id) {
        Scan scan = new Scan();
        scan.setId(id);
        return scan;
    }

    private static LlmAssessmentResult llmResult(String ingredient, String rootAllergen, double confidence) {
        return new LlmAssessmentResult(
                List.of(new ResolvedIngredient(ingredient, rootAllergen, confidence)),
                "evidence notes",
                "gpt-4o", 10, 5, 20L, "prompt", "response");
    }
}
