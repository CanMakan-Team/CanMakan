package com.canmakan.backend.ai.llm;

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
}
