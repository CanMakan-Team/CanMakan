package com.canmakan.backend.ai.llm;

<<<<<<< HEAD
/**
 * One piece of LLM evidence: an ingredient the model resolved to a root allergen,
 * with a confidence score. The engine only trusts it above a fixed confidence
 * threshold — the LLM never decides the verdict itself.
 *
 * <p>Shared contract between the orchestrator (consumer, HY) and the LLM layer
 * (owner, Member 3).
 *
 * @author XieHuayuan &amp; Member 3 (LLM layer) — shared contract
 */
public record ResolvedIngredient(
        String ingredientName,   // the ingredient as seen on the label
        String rootAllergen,     // resolved root allergen (e.g. "DAIRY"); null if unresolved
        double confidence        // 0.0 .. 1.0
) {
=======
import java.util.Objects;

/**
 * One ingredient resolution candidate returned as LLM evidence.
 *
 * @author YangMaowei
 */
public record ResolvedIngredient(
        String ingredientName,
        String rootAllergen,
        double confidence
) {

    public ResolvedIngredient {
        ingredientName = Objects.requireNonNull(
                ingredientName,
                "ingredientName must not be null"
        ).trim();
        if (ingredientName.isEmpty()) {
            throw new IllegalArgumentException("ingredientName must not be blank");
        }

        if (rootAllergen != null) {
            rootAllergen = rootAllergen.trim();
            if (rootAllergen.isEmpty()) {
                throw new IllegalArgumentException("rootAllergen must not be blank");
            }
        }

        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }
>>>>>>> origin/feat/integration-llm-audit-mw
}
