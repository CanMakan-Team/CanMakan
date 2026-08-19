package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;

import java.util.List;

/**
 * The seam between orchestration and rule matching. The {@link DietaryRuleEngine}
 * depends only on this interface; each restriction category has its own implementation,
 * so new rule types can be added without touching the engine.
 *
 * @author XieHuayuan
 */
public interface RestrictionChecker {

    /** Whether this checker handles the given restriction category. */
    boolean supports(RestrictionCategory category);

    /**
     * Evaluate one rule against the product and append any {@link Finding}s
     * (a checker adds nothing when the product is compliant).
     */
    void check(RestrictionRule rule, ProductData product, List<Finding> hits);
}
