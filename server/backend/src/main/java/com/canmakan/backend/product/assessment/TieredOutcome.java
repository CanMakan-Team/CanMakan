package com.canmakan.backend.product.assessment;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.product.verdict.SafetyVerdict;

/**
 * Result of the tiered assessment: the verdict actually returned to the caller, the tier
 * reached, and the LLM evidence used ({@code null} when the flow stayed at Tier 1).
 *
 * @author XieHuayuan
 */
public record TieredOutcome(
        SafetyVerdict verdict,
        ExecutionTier tier,
        LlmAssessmentResult llmResult
) {}
