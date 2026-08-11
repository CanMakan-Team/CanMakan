package com.canmakan.backend.product.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.ai.llm.ResolvedIngredient;
import com.canmakan.backend.ai.log.AiExecutionLogService;
import com.canmakan.backend.dietaryprofile.service.RestrictionRuleLoader;
import com.canmakan.backend.family.FamilyAuthorizationService;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanService;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link AssessmentOrchestrator}: it sequences Tier 1, delegates Tier 3 to
 * {@link LlmEscalationService}, then persists and logs the chosen tier. Tier-3 evidence
 * behaviour is covered in {@link LlmEscalationServiceTest}.
 *
 * @author XieHuayuan
 * @author Amelia
 */
@DisplayName("UC3: AssessmentOrchestrator tiered assess flow")
@ExtendWith(MockitoExtension.class)
class AssessmentOrchestratorTest {

    @Mock private ProductDataAdapter productDataAdapter;
    @Mock private RestrictionRuleLoader ruleLoader;
    @Mock private DietaryRuleEngine ruleEngine;
    @Mock private LlmEscalationService llmEscalationService;
    @Mock private ScanService scanService;
    @Mock private AiExecutionLogService aiExecutionLogService;
    @Mock private FamilyAuthorizationService familyAuthorization;

    @InjectMocks
    private AssessmentOrchestrator orchestrator;

    private static final AssessmentRequest REQUEST = new AssessmentRequest("123", 1L);

    @Test
    @DisplayName("UC2 BE5: assess rejects unauthorized profile before loading rules")
    void assessRejectsUnauthorizedProfile() {
        doThrow(new FamilyForbiddenException("Profile does not belong to your family circle."))
            .when(familyAuthorization)
            .assertProfileAuthorizedForScan(7L, 1L);

        assertThrows(
            FamilyForbiddenException.class,
            () -> orchestrator.assess(7L, REQUEST)
        );

        verifyNoInteractions(ruleLoader, productDataAdapter, ruleEngine, scanService);
    }

    @Test
    @DisplayName("UC3 BE1: Tier-1 outcome is logged as rules-only")
    void tierOneOutcomeIsLoggedAsRulesOnly() {
        stubLoadAndProduct(productWith(ingredient("Milk", "DAIRY")));
        SafetyVerdict safe = SafetyVerdict.safe("ok", List.of());
        when(llmEscalationService.escalate(any(), any(), any(), any()))
                .thenReturn(new TieredOutcome(safe, ExecutionTier.TIER_1_RULES, null));
        when(scanService.record(any(), any(), any(), any(), any())).thenReturn(scan(100L));

        AssessmentResponse response = orchestrator.assess(7L, REQUEST);

        assertEquals("SAFE", response.verdict());
        assertEquals(ExecutionTier.TIER_1_RULES, response.tier());
        assertEquals(100L, response.scanId());
        verify(aiExecutionLogService).recordRulesOnly(eq(100L), anyLong());
        verify(aiExecutionLogService, never()).record(anyLong(), any(), any());
    }

    @Test
    @DisplayName("UC3 BE2: Tier-3 outcome logs the LLM execution row")
    void tierThreeOutcomeLogsTheLlmRow() {
        stubLoadAndProduct(productWith(ingredient("Casein", null)));
        LlmAssessmentResult evidence = llmResult("Casein", "DAIRY", 0.9);
        when(llmEscalationService.escalate(any(), any(), any(), any()))
                .thenReturn(new TieredOutcome(
                        SafetyVerdict.unsafe("resolved to dairy", List.of()),
                        ExecutionTier.TIER_3_LLM, evidence));
        when(scanService.record(any(), any(), any(), any(), any())).thenReturn(scan(100L));

        AssessmentResponse response = orchestrator.assess(7L, REQUEST);

        assertEquals("UNSAFE", response.verdict());
        assertEquals(ExecutionTier.TIER_3_LLM, response.tier());
        verify(aiExecutionLogService).record(eq(100L), eq(ExecutionTier.TIER_3_LLM), any(LlmAssessmentResult.class));
        verify(aiExecutionLogService, never()).recordRulesOnly(anyLong(), anyLong());
    }

    @Test
    @DisplayName("UC3 BE3: escalation that stays on rules tier logs rules-only")
    void escalationThatStaysOnRulesTierLogsRulesOnly() {
        stubLoadAndProduct(productWith(ingredient("Casein", null)));
        when(llmEscalationService.escalate(any(), any(), any(), any()))
                .thenReturn(new TieredOutcome(
                        SafetyVerdict.warning("uncertain", List.of()),
                        ExecutionTier.TIER_1_RULES, null));
        when(scanService.record(any(), any(), any(), any(), any())).thenReturn(scan(100L));

        AssessmentResponse response = orchestrator.assess(7L, REQUEST);

        assertEquals("WARNING", response.verdict());
        assertEquals(ExecutionTier.TIER_1_RULES, response.tier());
        assertEquals("Test Product", response.productName());
        assertEquals("123", response.barcode());
        verify(aiExecutionLogService).recordRulesOnly(eq(100L), anyLong());
        verify(aiExecutionLogService, never()).record(anyLong(), any(), any());
    }

    @Test
    @DisplayName("UC3 BE4: null userId is rejected before assessment")
    void nullUserIdIsRejected() {
        assertThrows(
            AuthenticatedUserNotFoundException.class,
            () -> orchestrator.assess(null, REQUEST)
        );

        verifyNoInteractions(familyAuthorization, ruleLoader, productDataAdapter, ruleEngine, scanService);
    }

    @Test
    @DisplayName("UC3 BE5: Persist failure still returns verdict without crashing")
    void persistFailureStillReturnsVerdict() {
        stubLoadAndProduct(productWith(ingredient("Milk", "DAIRY")));
        when(llmEscalationService.escalate(any(), any(), any(), any()))
                .thenReturn(new TieredOutcome(
                        SafetyVerdict.safe("ok", List.of()), ExecutionTier.TIER_1_RULES, null));
        when(scanService.record(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("fk_scans_product"));

        AssessmentResponse response = orchestrator.assess(7L, REQUEST);

        assertEquals("SAFE", response.verdict());
        assertEquals(ExecutionTier.TIER_1_RULES, response.tier());
        assertNull(response.scanId());
        assertEquals("Test Product", response.productName());
        verifyNoInteractions(aiExecutionLogService);
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
            product.tracesTags(),
            product.nutrition(),
            product.dataComplete()
        );
        when(ruleLoader.load(1L)).thenReturn(List.of());
        when(productDataAdapter.lookup("123")).thenReturn(lookup);
        when(productDataAdapter.toProductData(lookup)).thenReturn(product);
    }

    private static ProductData productWith(Ingredient... ingredients) {
        return new ProductData("123", List.of(ingredients), "text", List.of(), null, true);
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
