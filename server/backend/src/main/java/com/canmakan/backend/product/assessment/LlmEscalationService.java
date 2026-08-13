package com.canmakan.backend.product.assessment;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.ai.llm.LlmClient;
import com.canmakan.backend.ai.llm.PromptBuilder;
import com.canmakan.backend.ai.llm.ResolvedIngredient;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.SafetyVerdict;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tier 3 of the assessment flow: when the deterministic engine is inconclusive, gather
 * <b>evidence</b> from the {@link LlmClient}, enrich the product with the trusted parts,
 * and let the {@link DietaryRuleEngine} re-decide.
 *
 * <p><b>The LLM never decides the verdict.</b> Only resolutions with a non-null allergen and
 * confidence &ge; {@link #LLM_CONFIDENCE_THRESHOLD} are trusted; everything else is ignored so
 * a shaky guess cannot drive a definitive verdict. Any AI failure degrades gracefully back to
 * the original Tier-1 result so a scan is never blocked.
 *
 * @author XieHuayuan
 * @author Amelia
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmEscalationService {

    /** LLM-resolved allergens below this confidence are treated as unresolved. */
    private static final double LLM_CONFIDENCE_THRESHOLD = 0.7;

    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final DietaryRuleEngine ruleEngine;

    /**
     * Escalate an inconclusive Tier-1 verdict. When the verdict is a WARNING on complete data,
     * build the evidence prompt, enrich the product, and re-run the engine (TIER_3_LLM);
     * otherwise (or on any AI failure) return the original Tier-1 result unchanged.
     *
     * @param rules        the profile's active restrictions
     * @param product      the product snapshot assessed at Tier 1
     * @param tier1Verdict the deterministic Tier-1 verdict
     * @param barcode      the scanned barcode (for log context)
     * @return the final verdict, the tier reached, and any LLM evidence used
     */
    public TieredOutcome escalate(
            List<RestrictionRule> rules,
            ProductData product,
            SafetyVerdict tier1Verdict,
            String barcode) {
        if (!shouldEscalate(tier1Verdict, product)) {
            return new TieredOutcome(tier1Verdict, ExecutionTier.TIER_1_RULES, null);
        }
        try {
            // The LLM may only supply roots for ingredients the deterministic engine could not
            // resolve. Anything the engine already classified (a real allergen, or catalog
            // known-safe such as Water/Sugar/Salt) is authoritative and must not be overwritten
            // by an LLM guess - otherwise a shaky guess tags every ingredient with the
            // restriction and the whole label reads as matching.
            Set<String> unresolvedKeys = unresolvedIngredientKeys(tier1Verdict);
            String compiledPrompt = promptBuilder.build(product, rules);
            LlmAssessmentResult llmResult = llmClient.assess(compiledPrompt);   // evidence only, no verdict
            ProductData enriched = enrichWithLlmEvidence(product, llmResult, unresolvedKeys);
            SafetyVerdict reDecided = ruleEngine.assess(rules, enriched);       // engine re-decides
            return new TieredOutcome(reDecided, ExecutionTier.TIER_3_LLM, llmResult);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            log.warn(
                "Tier-3 escalate skipped for barcode {}; keeping Tier-1 WARNING: {}",
                barcode,
                ex.getMessage()
            );
            return new TieredOutcome(tier1Verdict, ExecutionTier.TIER_1_RULES, null);
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
     * {@link #LLM_CONFIDENCE_THRESHOLD} are trusted. Returns a new {@link ProductData}
     * for the engine to re-assess deterministically.
     */
    private ProductData enrichWithLlmEvidence(
            ProductData product, LlmAssessmentResult llm, Set<String> unresolvedKeys) {
        if (product == null || product.ingredients() == null
                || llm == null || llm.resolvedIngredients() == null
                || llm.resolvedIngredients().isEmpty()
                || unresolvedKeys == null || unresolvedKeys.isEmpty()) {
            return product;
        }

        // Keep only trusted evidence: a named ingredient, a concrete allergen, confidence >= threshold.
        Map<String, String> trusted = llm.resolvedIngredients().stream()
                .filter(ri -> ri.ingredientName() != null
                        && ri.rootAllergen() != null && !ri.rootAllergen().isBlank()
                        && ri.confidence() >= LLM_CONFIDENCE_THRESHOLD)
                .collect(Collectors.toMap(
                        ri -> normalizedKey(ri.ingredientName()),
                        ResolvedIngredient::rootAllergen,
                        (a, b) -> a));

        if (trusted.isEmpty()) {
            return product;
        }

        // Fill a trusted allergen only into ingredients the engine left UNRESOLVED at Tier 1.
        // Ingredients already resolved (a real allergen) or catalog known-safe are authoritative
        // and are never overwritten by LLM evidence.
        List<Ingredient> merged = new ArrayList<>();
        for (Ingredient ing : product.ingredients()) {
            String key = normalizedKey(ing.ingredientName());
            boolean needsRoot = ing.rootAllergen() == null || ing.rootAllergen().isBlank();
            boolean wasUnresolved = key != null && unresolvedKeys.contains(key);
            if (needsRoot && wasUnresolved && trusted.containsKey(key)) {
                merged.add(new Ingredient(
                        ing.ingredientName(), ing.parentAllergen(), trusted.get(key), ing.chemicalAlias()));
            } else {
                merged.add(ing);
            }
        }

        return new ProductData(product.barcode(), merged, product.ingredientsText(),
            product.labelTags(), product.tracesTags(), product.nutrition(), product.dataComplete());
    }

    /**
     * The ingredient names the deterministic engine could not resolve at Tier 1, taken from the
     * grouped {@link DietaryRuleEngine#UNRESOLVED} finding. These are the only ingredients the LLM
     * is allowed to supply a root allergen for.
     */
    private static Set<String> unresolvedIngredientKeys(SafetyVerdict tier1Verdict) {
        Set<String> keys = new HashSet<>();
        if (tier1Verdict == null || tier1Verdict.findings() == null) {
            return keys;
        }
        for (Finding finding : tier1Verdict.findings()) {
            if (!DietaryRuleEngine.UNRESOLVED.equals(finding.restrictionCode())
                    || finding.ingredientName() == null) {
                continue;
            }
            // The finding groups the unresolved names as a comma-separated list.
            for (String name : finding.ingredientName().split(",")) {
                String key = normalizedKey(name.trim());
                if (key != null && !key.isBlank()) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    /** Lower-cases an ingredient name for case-insensitive matching; {@code null} stays {@code null}. */
    private static String normalizedKey(String ingredientName) {
        return ingredientName == null ? null : ingredientName.toLowerCase(Locale.ROOT);
    }
}
