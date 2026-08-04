package com.canmakan.backend.ai.llm;

import com.canmakan.backend.product.verdict.Finding;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Non-authoritative model evidence plus execution metadata for audit logging.
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
public record LlmAssessmentResult(
        List<Finding> proposedFindings,
        List<String> unresolvedIngredients,
        Map<String, String> resolvedNames,
        BigDecimal confidence,
        String explanation,
        String modelId,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        long latencyMs,
        String promptVersion,
        String correlationId,
        List<String> toolCallSummary,
        String compiledPrompt,
        String rawResponse,
        Status status,
        String errorMessage
) {

    public LlmAssessmentResult {
        proposedFindings = proposedFindings == null ? List.of() : List.copyOf(proposedFindings);
        unresolvedIngredients = unresolvedIngredients == null
                ? List.of()
                : List.copyOf(unresolvedIngredients);
        resolvedNames = resolvedNames == null ? Map.of() : Map.copyOf(resolvedNames);
        toolCallSummary = toolCallSummary == null ? List.of() : List.copyOf(toolCallSummary);
        status = Objects.requireNonNull(status, "status");

        if (totalTokens == null && inputTokens != null && outputTokens != null) {
            totalTokens = inputTokens + outputTokens;
        }
    }

    public boolean successful() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        PROVIDER_UNAVAILABLE,
        PROVIDER_ERROR,
        TIMEOUT,
        INVALID_RESPONSE,
        TOOL_ERROR,
        INTERRUPTED
    }
}
