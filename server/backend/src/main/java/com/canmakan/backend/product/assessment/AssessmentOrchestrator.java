package com.canmakan.backend.product.assessment;

import com.canmakan.backend.ai.llm.LlmClient;
import com.canmakan.backend.ai.log.AiExecutionLogService;
import com.canmakan.backend.dietaryprofile.RestrictionRuleLoader;
import com.canmakan.backend.product.scan.ScanService;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import org.springframework.stereotype.Service;

/**
 * Coordinates the tiered "scan product barcode -> view scan verdict" flow:
 *
 * <ol>
 *   <li>load the profile's active restriction rules (from the dietary preferences)</li>
 *   <li>build {@link ProductData} for the barcode</li>
 *   <li>run the deterministic {@link DietaryRuleEngine} (TIER_1_RULES)</li>
 *   <li>escalate to the {@link LlmClient} only when the rules are inconclusive (TIER_3_LLM)</li>
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
    private final LlmClient llmClient;
    private final ScanService scanService;
    private final AiExecutionLogService aiExecutionLogService;

    public AssessmentOrchestrator(ProductDataAdapter productDataAdapter,
                                  RestrictionRuleLoader ruleLoader,
                                  DietaryRuleEngine ruleEngine,
                                  LlmClient llmClient,
                                  ScanService scanService,
                                  AiExecutionLogService aiExecutionLogService) {
        this.productDataAdapter = productDataAdapter;
        this.ruleLoader = ruleLoader;
        this.ruleEngine = ruleEngine;
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
        // TODO:
        //   List<RestrictionRule> rules = ruleLoader.load(request.profileId());
        //   ProductData product = productDataAdapter.toProductData(request.barcode());
        //   SafetyVerdict verdict = ruleEngine.assess(rules, product);
        //   ExecutionTier tier = TIER_1_RULES;
        //   if (shouldEscalate(verdict, product)) { ... llmClient.assess(...); tier = TIER_3_LLM; }
        //   Scan scan = scanService.record(userId, request.profileId(), request.barcode(), verdict);
        //   aiExecutionLogService.record(scan.getId(), tier, ...);
        //   return new AssessmentResponse(...);
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Whether the rule-engine result is inconclusive enough to escalate to the LLM
     * (e.g. WARNING / unresolved ingredients / incomplete data).
     */
    private boolean shouldEscalate(SafetyVerdict verdict, ProductData product) {
        // TODO: define escalation policy.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
