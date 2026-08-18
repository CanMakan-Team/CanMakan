package com.canmakan.backend.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests the immutable evidence-oriented LLM result contract.
 *
 * @author YangMaowei
 */
class LlmAssessmentResultTest {

    @Test
    void constructsCompleteEvidenceResult() {
        ResolvedIngredient ingredient = new ResolvedIngredient("Milk", "DAIRY", 0.95);

        LlmAssessmentResult result = new LlmAssessmentResult(
                List.of(ingredient),
                "Milk is associated with the dairy root allergen.",
                "test-model",
                10,
                5,
                25L,
                "compiled prompt",
                "raw response"
        );

        assertEquals(List.of(ingredient), result.resolvedIngredients());
        assertEquals("Milk is associated with the dairy root allergen.", result.analysisNotes());
        assertEquals("test-model", result.modelId());
        assertEquals(10, result.promptTokens());
        assertEquals(5, result.completionTokens());
        assertEquals(25L, result.latencyMs());
        assertEquals("compiled prompt", result.compiledPrompt());
        assertEquals("raw response", result.rawResponse());
    }

    @Test
    void defensivelyCopiesResolvedIngredientsAndReturnsImmutableList() {
        ResolvedIngredient ingredient = new ResolvedIngredient("Milk", "DAIRY", 0.95);
        List<ResolvedIngredient> source = new ArrayList<>(List.of(ingredient));
        LlmAssessmentResult result = result(source, "Evidence notes.", null, null, 0L);

        source.clear();

        assertEquals(List.of(ingredient), result.resolvedIngredients());
        List<ResolvedIngredient> resolvedIngredients = result.resolvedIngredients();
        assertThrows(
                UnsupportedOperationException.class,
                () -> resolvedIngredients.add(ingredient)
        );
    }

    @Test
    void rejectsNullListAndNullElements() {
        assertThrows(
                NullPointerException.class,
                () -> result(null, "Evidence notes.", null, null, 0L)
        );
        assertThrows(
                NullPointerException.class,
                () -> result(
                        Collections.singletonList(null),
                        "Evidence notes.",
                        null,
                        null,
                        0L
                )
        );
    }

    @Test
    void preservesAnalysisNotesAsExplanationAndNormalizesNullToEmpty() {
        assertEquals(
                "Evidence remains uncertain.",
                result(List.of(), "Evidence remains uncertain.", null, null, 0L).analysisNotes()
        );
        assertEquals("", result(List.of(), null, null, null, 0L).analysisNotes());
    }

    @Test
    void rejectsSensitiveAnalysisNotesWithoutEchoingTheSecret() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> result(List.of(), "Authorization: Bearer private-value", null, null, 0L)
        );

        assertEquals(
                "analysisNotes must not contain sensitive credentials",
                exception.getMessage()
        );
    }

    @Test
    void rejectsBearerTokenWithoutKeyValueSyntax() {
        // No "keyword: " / "keyword=" pair here, so this only trips the bearer-token pattern,
        // not the key/value pattern — exercises that branch of the OR independently.
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> result(List.of(), "Bearer abc123xyz", null, null, 0L)
        );

        assertEquals(
                "analysisNotes must not contain sensitive credentials",
                exception.getMessage()
        );
    }

    @Test
    void allowsNullTokenMetadata() {
        LlmAssessmentResult result = result(List.of(), "", null, null, 0L);

        assertNull(result.promptTokens());
        assertNull(result.completionTokens());
    }

    @Test
    void rejectsNegativeTokenAndLatencyMetadata() {
        assertThrows(
                IllegalArgumentException.class,
                () -> result(List.of(), "", -1, null, 0L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> result(List.of(), "", null, -1, 0L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> result(List.of(), "", null, null, -1L)
        );
    }

    @Test
    void hasNoVerdictOrReasonAccessor() {
        assertThrows(
                NoSuchMethodException.class,
                () -> LlmAssessmentResult.class.getMethod("verdict")
        );
        assertThrows(
                NoSuchMethodException.class,
                () -> LlmAssessmentResult.class.getMethod("reason")
        );
    }

    private static LlmAssessmentResult result(
            List<ResolvedIngredient> ingredients,
            String notes,
            Integer promptTokens,
            Integer completionTokens,
            long latencyMs
    ) {
        return new LlmAssessmentResult(
                ingredients,
                notes,
                "test-model",
                promptTokens,
                completionTokens,
                latencyMs,
                null,
                null
        );
    }
}
