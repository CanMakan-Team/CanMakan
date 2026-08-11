package com.canmakan.backend.product.assessment;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.ai.log.AiExecutionLogService;
import com.canmakan.backend.family.FamilyAuthorizationService;
import com.canmakan.backend.dietaryprofile.service.RestrictionRuleLoader;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanService;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Coordinates the tiered "scan product barcode -> view scan verdict" flow.
 *
 * <p>Used by {@code POST /api/scan} on {@link com.canmakan.backend.product.scan.ScanController}.
 * Product data comes from a <b>single</b> Open Food Facts {@code fetchProduct}
 * via {@link ProductDataAdapter#lookup}; this path does not call
 * {@code validateProduct} and does not use EAN-Search.
 *
 * <ol>
 *   <li>load the profile's active restriction rules (from the saved dietary preferences)</li>
 *   <li>lookup the product once (OFF) and build {@link ProductData}</li>
 *   <li>run the deterministic {@link DietaryRuleEngine} (TIER_1_RULES)</li>
 *   <li>if inconclusive, hand off to {@link LlmEscalationService} for evidence + re-decide (TIER_3_LLM)</li>
 *   <li>persist the scan and its execution log, then return the verdict</li>
 * </ol>
 *
 * <p><b>The LLM never decides the verdict.</b> Tier 3 only supplies evidence; the final
 * verdict always comes from {@link DietaryRuleEngine}.
 *
 * @author XieHuayuan
 * @author Amelia
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentOrchestrator {

    private final ProductDataAdapter productDataAdapter;
    private final RestrictionRuleLoader ruleLoader;
    private final DietaryRuleEngine ruleEngine;
    private final LlmEscalationService llmEscalationService;
    private final ScanService scanService;
    private final AiExecutionLogService aiExecutionLogService;
    private final FamilyAuthorizationService familyAuthorization;

    /**
     * Assess one product for one profile and persist the outcome.
     *
     * Tier 1: deterministic rule engine (timed for the audit log).
     * Tier 3: on an inconclusive WARNING, {@link LlmEscalationService} gets LLM evidence,
     * enriches, and the engine re-decides. AI failures keep the Tier-1 result.
     *
     * @param userId  the scanning user (from the auth token)
     * @param request barcode + profileId
     * @return the verdict, chosen tier, and saved scan id
     */
    public AssessmentResponse assess(Long userId, AssessmentRequest request) {
        if (userId == null) {
            throw new AuthenticatedUserNotFoundException("Authenticated user was not found.");
        }
        familyAuthorization.assertProfileAuthorizedForScan(userId, request.profileId());
        List<RestrictionRule> rules = ruleLoader.load(request.profileId());
        var lookup = productDataAdapter.lookup(request.barcode());
        ProductData product = productDataAdapter.toProductData(lookup);

        // Tier 1: deterministic rule engine, timed for the audit log.
        long start = System.nanoTime();
        SafetyVerdict tier1Verdict = ruleEngine.assess(rules, product);
        long ruleLatencyMs = (System.nanoTime() - start) / 1_000_000;

        // Tier 3: escalate only on an inconclusive WARNING; falls back to Tier-1 on failure.
        TieredOutcome outcome = llmEscalationService.escalate(rules, product, tier1Verdict, request.barcode());

        // Persistence failure must not hide a successful verdict from the client.
        String productName = lookup.productName() == null || lookup.productName().isBlank()
                ? "Unknown product"
                : lookup.productName();

        Long scanId = persistScanAndLog(
                userId, request, outcome.verdict(), productName,
                outcome.tier(), outcome.llmResult(), ruleLatencyMs);

        return new AssessmentResponse(
                outcome.verdict().toScansVerdict(),
                outcome.verdict().explanation(),
                outcome.verdict().findings(),
                outcome.tier(),
                scanId,
                productName,
                request.barcode());
    }

    /**
     * Best-effort persistence. Returns the saved scan id, or {@code null} when
     * the DB write fails (verdict is still returned to the caller).
     */
    private Long persistScanAndLog(
            Long userId,
            AssessmentRequest request,
            SafetyVerdict verdict,
            String productName,
            ExecutionTier tier,
            LlmAssessmentResult llmResult,
            long ruleLatencyMs
    ) {
        try {
            Scan scan = scanService.record(
                    userId, request.profileId(), request.barcode(), verdict, productName);
            Long scanId = scan == null ? null : scan.getId();
            if (scanId == null) {
                return null;
            }
            try {
                if (tier == ExecutionTier.TIER_3_LLM) {
                    aiExecutionLogService.record(scanId, tier, llmResult);
                } else {
                    aiExecutionLogService.recordRulesOnly(scanId, ruleLatencyMs);
                }
            } catch (DataAccessException | IllegalArgumentException | IllegalStateException ex) {
                log.warn(
                    "Assess verdict OK but AI/execution log persist failed for barcode {}: {}",
                    request.barcode(),
                    ex.getMessage()
                );
            }
            return scanId;
        } catch (DataAccessException | IllegalArgumentException | IllegalStateException ex) {
            log.warn(
                "Assess verdict OK but scan persist failed for barcode {}: {}",
                request.barcode(),
                ex.getMessage()
            );
            return null;
        }
    }
}
