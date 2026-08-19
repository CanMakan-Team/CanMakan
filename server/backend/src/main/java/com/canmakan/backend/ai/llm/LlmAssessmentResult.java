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

    // Split into two simpler patterns (each with a single shared quantifier per branch) instead of
    // one five-way alternation repeating "\s*[:=]" per branch, which pushed the combined regex past
    // SonarQube's complexity threshold. Behavior is unchanged: either pattern matching is a rejection.
    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile(
            "(?i)(authorization|api[_ -]?key|token|secret)\\s*[:=]"
    );
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+");

    public LlmAssessmentResult {
        resolvedIngredients = List.copyOf(
                Objects.requireNonNull(resolvedIngredients, "resolvedIngredients must not be null")
        );
        analysisNotes = analysisNotes == null ? "" : analysisNotes;

        if (SENSITIVE_KEY_VALUE.matcher(analysisNotes).find()
                || BEARER_TOKEN.matcher(analysisNotes).find()) {
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
