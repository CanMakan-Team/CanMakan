package com.canmakan.backend.product.assessment;

/**
 * Which path produced a scan verdict. Persisted to
 * {@code ai_execution_logs.execution_tier}.
 *
 * @author XieHuayuan
 */
public enum ExecutionTier {

    /** Deterministic rule engine only; no LLM call (fast path). */
    TIER_1_RULES,

    /** Escalated to the LLM for deeper ingredient reasoning. */
    TIER_3_LLM
}
