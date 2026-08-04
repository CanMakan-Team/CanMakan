package com.canmakan.backend.product.assessment;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.ai.llm.LlmClient;
import com.canmakan.backend.ai.llm.PromptBuilder;
import com.canmakan.backend.ai.log.AiExecutionLogService;
import com.canmakan.backend.dietaryprofile.RestrictionRuleLoader;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanService;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Coordinates the tiered "scan product barcode -> view scan verdict" flow:
 *
 * <ol>
 *   <li>load the profile's active restriction rules (from the saved dietary preferences)</li>
 *   <li>build {@link ProductData} for the barcode</li>
 *   <li>run the deterministic {@link DietaryRuleEngine} (TIER_1_RULES)</li>
 *   <li>escalate to the {@link LlmClient} only when the rule result is inconclusive (TIER_3_LLM)</li>
 *   <li>persist the scan and its execution log, then return the verdict</li>
 * </ol>
 *
 * @author XieHuayuan
 */
@Service
public class AssessmentOrchestrator {

    private final ProductDataAdapter productDataAdapter;
    private final RestrictionRuleLoader ruleLoader;
    private final DietaryRuleEngine ruleEngine;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final ScanService scanService;
    private final AiExecutionLogService aiExecutionLogService;

    public AssessmentOrchestrator(ProductDataAdapter productDataAdapter,
                                  RestrictionRuleLoader ruleLoader,
                                  DietaryRuleEngine ruleEngine,
                                  PromptBuilder promptBuilder,
                                  LlmClient llmClient,
                                  ScanService scanService,
                                  AiExecutionLogService aiExecutionLogService) {
        this.productDataAdapter = productDataAdapter;
        this.ruleLoader = ruleLoader;
        this.ruleEngine = ruleEngine;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.scanService = scanService;
        this.aiExecutionLogService = aiExecutionLogService;
    }

    /**
     * Assess one product for one profile and persist the outcome.
     *
     * @param userId  the scanning user (from the auth token)
     * @param request barcode + profileId
     * @return the verdict, chosen tier, and saved scan id
     */
    public AssessmentResponse assess(Long userId, AssessmentRequest request) {
        List<RestrictionRule> rules = ruleLoader.load(request.profileId());
        ProductData product = productDataAdapter.toProductData(request.barcode());

        // TIER 1: deterministic rule engine (timed for the audit log).
        long start = System.nanoTime();
        SafetyVerdict verdict = ruleEngine.assess(rules, product);
        long ruleLatencyMs = (System.nanoTime() - start) / 1_000_000;

        ExecutionTier tier = ExecutionTier.TIER_1_RULES;
        LlmAssessmentResult llmResult = null;

        // TIER 3: escalate only the inconclusive middle to the LLM.
        if (shouldEscalate(verdict, product)) {
            String compiledPrompt = promptBuilder.build(product, rules);
            llmResult = llmClient.assess(compiledPrompt);
            verdict = applyLlmVerdict(verdict, llmResult);
            tier = ExecutionTier.TIER_3_LLM;
        }

        // Persist the scan, then the matching execution-log row.
        Scan scan = scanService.record(userId, request.profileId(), request.barcode(), verdict);
        if (tier == ExecutionTier.TIER_3_LLM) {
            aiExecutionLogService.record(scan.getId(), tier, llmResult);
        } else {
            aiExecutionLogService.recordRulesOnly(scan.getId(), ruleLatencyMs);
        }

        return new AssessmentResponse(
                verdict.toScansVerdict(),
                verdict.explanation(),
                verdict.findings(),
                tier,
                scan.getId());
    }

    /**
     * Escalation policy: SAFE and UNSAFE are definitive, so only a WARNING
     * (intolerance, unresolved ingredient, or uncertain religious/diet compliance)
     * is worth the LLM's deeper reasoning — and only when we actually have data.
     */
    private boolean shouldEscalate(SafetyVerdict verdict, ProductData product) {
        return verdict.level() == SafetyVerdict.Level.WARNING
                && product != null && product.dataComplete();
    }

    /**
     * Overlay the LLM's structured verdict on top of the rule result, keeping the
     * engine's findings. Falls back to the rule verdict if the LLM output is unusable.
     */
    private SafetyVerdict applyLlmVerdict(SafetyVerdict ruleVerdict, LlmAssessmentResult llm) {
        if (llm == null || llm.verdict() == null) {
            return ruleVerdict;
        }
        try {
            SafetyVerdict.Level level = SafetyVerdict.Level.valueOf(llm.verdict().trim().toUpperCase());
            String explanation = (llm.reason() == null || llm.reason().isBlank())
                    ? ruleVerdict.explanation()
                    : llm.reason();
            return new SafetyVerdict(level, explanation, ruleVerdict.findings());
        } catch (IllegalArgumentException e) {
            return ruleVerdict;   // LLM returned an unexpected level -> trust the engine
        }
    }
}
