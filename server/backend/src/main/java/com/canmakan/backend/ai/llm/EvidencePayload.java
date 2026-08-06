package com.canmakan.backend.ai.llm;

import java.util.List;

/**
 * Structured Tier-3 evidence returned by the ChatClient entity converter.
 * Mirrors the evidence JSON schema; the rule engine still owns the verdict.
 *
 * @author Amelia
 */
public record EvidencePayload(
        List<ResolvedIngredientEvidence> resolvedIngredients,
        String analysisNotes
) {

        /**
         * One ingredient evidence row from the model.
         */
        public record ResolvedIngredientEvidence(
                String ingredientName,
                String rootAllergen,
                Double confidence
        ) {
        }
}
