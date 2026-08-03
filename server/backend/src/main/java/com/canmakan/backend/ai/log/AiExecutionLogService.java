package com.canmakan.backend.ai.log;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.product.assessment.ExecutionTier;
import org.springframework.stereotype.Service;

/**
 * Writes the audit trail into {@code ai_execution_logs} after each assessment.
 *
 * @author XieHuayuan
 */
@Service
public class AiExecutionLogService {

    private final AiExecutionLogRepository repository;

    public AiExecutionLogService(AiExecutionLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Log a TIER_1_RULES (deterministic, no LLM) execution.
     *
     * @param scanId    the saved scan
     * @param latencyMs rule-engine latency
     */
    public AiExecutionLog recordRulesOnly(Long scanId, long latencyMs) {
        // TODO: build entity (execution_tier = TIER_1_RULES, no model/tokens) and save.
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Log a TIER_3_LLM execution, including the model, tokens, prompt and raw response.
     *
     * @param scanId the saved scan
     * @param tier   the tier used
     * @param llm    the LLM result carrying usage metadata
     */
    public AiExecutionLog record(Long scanId, ExecutionTier tier, LlmAssessmentResult llm) {
        // TODO: map LlmAssessmentResult -> AiExecutionLog and save.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
