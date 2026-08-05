package com.canmakan.backend.ai.llm;

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
}
