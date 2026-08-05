package com.canmakan.backend.ai.llm;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Structured ingredient evidence from an LLM assessment plus metadata needed for the
 * {@code ai_execution_logs} audit trail.
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
public record LlmAssessmentResult(
        List<ResolvedIngredient> resolvedIngredients,
        String analysisNotes,
        String modelId,
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs,
        String compiledPrompt,
        String rawResponse
) {

    private static final Pattern SENSITIVE_CONTENT = Pattern.compile(
            "(?i)(authorization\\s*[:=]|bearer\\s+|api[_ -]?key\\s*[:=]"
                    + "|token\\s*[:=]|secret\\s*[:=])"
    );

    public LlmAssessmentResult {
        resolvedIngredients = List.copyOf(
                Objects.requireNonNull(resolvedIngredients, "resolvedIngredients must not be null")
        );
        analysisNotes = analysisNotes == null ? "" : analysisNotes;

        if (SENSITIVE_CONTENT.matcher(analysisNotes).find()) {
            throw new IllegalArgumentException(
                    "analysisNotes must not contain sensitive credentials"
            );
        }
        if (promptTokens != null && promptTokens < 0) {
            throw new IllegalArgumentException("promptTokens must not be negative");
        }
        if (completionTokens != null && completionTokens < 0) {
            throw new IllegalArgumentException("completionTokens must not be negative");
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
    }
}
