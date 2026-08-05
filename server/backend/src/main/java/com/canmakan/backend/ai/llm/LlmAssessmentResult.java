package com.canmakan.backend.ai.llm;

import java.util.List;

/**
 * Evidence the LLM returns when the deterministic engine escalates, plus the
 * metadata needed for the {@code ai_execution_logs} audit trail.
 *
 * <p><b>The LLM does not decide the verdict.</b> It only supplies evidence
 * ({@link #resolvedIngredients}) that the {@code DietaryRuleEngine} feeds back
 * through its deterministic rules to reach the verdict. {@link #analysisNotes} is
 * display/audit text only and must never influence the verdict.
 *
 * <p>Shared contract between the orchestrator (consumer, HY) and the LLM layer
 * (owner, Member 3): the record shape is agreed here; Member 3 implements how it
 * is produced (LlmClient / PromptBuilder) and logged.
 *
 * @author XieHuayuan &amp; Member 3 (LLM layer) — shared contract
 */
public record LlmAssessmentResult(
        List<ResolvedIngredient> resolvedIngredients,  // evidence: ingredient -> root allergen
        String analysisNotes,                          // explanation only -> ai_execution_logs; NOT used to decide
        // --- audit metadata (ai_execution_logs) ---
        String modelId,                                // e.g. "gpt-4o"
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs,
        String compiledPrompt,                         // -> ai_execution_logs.compiled_prompt
        String rawResponse                             // -> ai_execution_logs.raw_llm_response
) {
}
