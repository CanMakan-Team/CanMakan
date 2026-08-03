package com.canmakan.backend.ai.llm;

/**
 * Structured output of an LLM assessment plus the metadata needed for the
 * {@code ai_execution_logs} audit trail.
 *
 * @author XieHuayuan
 */
public record LlmAssessmentResult(
        String verdict,          // SAFE / WARNING / UNSAFE
        String reason,
        String modelId,          // e.g. "gpt-4o"
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs,
        String compiledPrompt,   // stored to ai_execution_logs.compiled_prompt
        String rawResponse       // stored to ai_execution_logs.raw_llm_response
) {
}
