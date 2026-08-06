package com.canmakan.backend.product.assessment;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.ai.llm.LlmClient;
import com.canmakan.backend.ai.llm.PromptBuilder;
import com.canmakan.backend.ai.llm.ResolvedIngredient;
import com.canmakan.backend.ai.log.AiExecutionLogService;
import com.canmakan.backend.dietaryprofile.RestrictionRuleLoader;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanService;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.SafetyVerdict;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
 *   <li>if inconclusive, ask the {@link LlmClient} for <b>evidence</b> (resolved ingredients),
 *       enrich the product data with it, and <b>re-run the engine</b> (TIER_3_LLM)</li>
 *   <li>persist the scan and its execution log, then return the verdict</li>
 * </ol>
 *
 * <p><b>The LLM never decides the verdict.</b> It only supplies evidence; the final
 * verdict always comes from {@link DietaryRuleEngine}. LLM-resolved allergens are
 * trusted only above {@link #LLM_CONFIDENCE_THRESHOLD}, otherwise the ingredient
 * stays unresolved and the engine degrades to WARNING.
 *
 * @author XieHuayuan
 * @author Amelia
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentOrchestrator {

    /** LLM-resolved allergens below this confidence are treated as unresolved. */
    private static final double LLM_CONFIDENCE_THRESHOLD = 0.7;

    private final ProductDataAdapter productDataAdapter;
    private final RestrictionRuleLoader ruleLoader;
    private final DietaryRuleEngine ruleEngine;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final ScanService scanService;
    private final AiExecutionLogService aiExecutionLogService;

    /**
     * Assess one product for one profile and persist the outcome.
     * 
     * Tier 1: deterministic rule engine (timed for the audit log).
     * Tier 3: on an inconclusive WARNING, get LLM EVIDENCE, enrich, and re-decide.
     * If AI is disabled or the provider fails, keep the Tier-1 WARNING result.
     *
     * @param userId  the scanning user (from the auth token)
     * @param request barcode + profileId
     * @return the verdict, chosen tier, and saved scan id
     */
    public AssessmentResponse assess(Long userId, AssessmentRequest request) {
        List<RestrictionRule> rules = ruleLoader.load(request.profileId());
        var lookup = productDataAdapter.lookup(request.barcode());
        ProductData product = productDataAdapter.toProductData(lookup);

        // TIER 1: deterministic rule engine (timed for the audit log).
        long start = System.nanoTime();

        // Assess the product using the rule engine which 
        // assesses the product against the profile's active restrictions.
        SafetyVerdict verdict = ruleEngine.assess(rules, product);
        long ruleLatencyMs = (System.nanoTime() - start) / 1_000_000;

        ExecutionTier tier = ExecutionTier.TIER_1_RULES;
        LlmAssessmentResult llmResult = null;

        // TIER 3: on an inconclusive WARNING, get LLM EVIDENCE, enrich, and re-decide.
        // If AI is disabled or the provider fails, keep the Tier-1 WARNING result.
        if (shouldEscalate(verdict, product)) {
            try {

                // A. Build the prompt for the LLM
                // which is a list of ingredients and their parent allergens.
                String compiledPrompt = promptBuilder.build(product, rules);
                llmResult = llmClient.assess(compiledPrompt);          // evidence only, no verdict
                
                // B. Enrich the product data with the LLM evidence.
                ProductData enriched = enrichWithLlmEvidence(product, llmResult);
                
                // C. Re-assess the product using the rule engine.
                verdict = ruleEngine.assess(rules, enriched);         // engine re-decides (deterministic)
                tier = ExecutionTier.TIER_3_LLM;

            } catch (IllegalStateException | IllegalArgumentException ex) {
                log.warn(
                    "Tier-3 escalate skipped for barcode {}; keeping Tier-1 WARNING: {}",
                    request.barcode(),
                    ex.getMessage()
                );
                llmResult = null;
                tier = ExecutionTier.TIER_1_RULES;
            }
        }

        // Persist the scan, then the matching execution-log row.
        // Persistence failure must not hide a successful verdict from the client.
        String productName = lookup.productName() == null || lookup.productName().isBlank()
                ? "Unknown product"
                : lookup.productName();

        Long scanId = persistScanAndLog(userId, request, verdict, productName, tier, llmResult, ruleLatencyMs);

        return new AssessmentResponse(
                verdict.toScansVerdict(),
                verdict.explanation(),
                verdict.findings(),
                tier,
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
     * Overlay the LLM's evidence onto ingredients that still have no root allergen.
     * Only resolutions with a non-null allergen and confidence &ge;
     * {@link #LLM_CONFIDENCE_THRESHOLD} are trusted; everything else is ignored so
     * a shaky guess cannot drive a definitive verdict. Returns a new
     * {@link ProductData} for the engine to re-assess deterministically.
     */
    private ProductData enrichWithLlmEvidence(ProductData product, LlmAssessmentResult llm) {
        if (product == null || product.ingredients() == null
                || llm == null || llm.resolvedIngredients() == null
                || llm.resolvedIngredients().isEmpty()) {
            return product;
        }

        // Filter the resolved ingredients to only include those with
        // a non-null allergen and confidence >= {@link #LLM_CONFIDENCE_THRESHOLD}
        Map<String, String> trusted = llm.resolvedIngredients().stream()
                .filter(ri -> ri.ingredientName() != null
                        && ri.rootAllergen() != null && !ri.rootAllergen().isBlank()
                        && ri.confidence() >= LLM_CONFIDENCE_THRESHOLD)
                .collect(Collectors.toMap(
                        ri -> ri.ingredientName().toLowerCase(Locale.ROOT),
                        ResolvedIngredient::rootAllergen,
                        (a, b) -> a));

        // If there are no trusted resolved ingredients, return the original product data.
        if (trusted.isEmpty()) {
            return product;
        }

        // Merge the trusted resolved ingredients with the original product data.
        List<Ingredient> merged = new ArrayList<>();
        for (Ingredient ing : product.ingredients()) {
            String key = ing.ingredientName() == null
                    ? null : ing.ingredientName().toLowerCase(Locale.ROOT);
            boolean needsRoot = ing.rootAllergen() == null || ing.rootAllergen().isBlank();
            if (needsRoot && key != null && trusted.containsKey(key)) {
                merged.add(new Ingredient(
                        ing.ingredientName(), ing.parentAllergen(), trusted.get(key), ing.chemicalAlias()));
            } else {
                merged.add(ing);
            }
        }

        // Return the enriched product data.
        return new ProductData(product.barcode(), merged, product.ingredientsText(),
            product.labelTags(), product.tracesTags(), product.nutrition(), product.dataComplete());
    }
}
