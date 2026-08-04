package com.canmakan.backend.ai.log;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.product.assessment.ExecutionTier;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Writes the audit trail into {@code ai_execution_logs} after each assessment.
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
@Service
public class AiExecutionLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiExecutionLogService.class);
    private static final Pattern BEARER_TOKEN = Pattern.compile(
        "(?i)\\bBearer\\s+[^\\s,;\\\"}]+"
    );
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
        "(?i)\\b(api[_-]?key|authorization|token|secret)"
            + "(\\\"?\\s*[:=]\\s*\\\"?)[^\\s,;\\\"}]+"
    );

    private final AiExecutionLogRepository repository;
    private final boolean auditEnabled;
    private final boolean rawPromptEnabled;
    private final boolean rawResponseEnabled;

    public AiExecutionLogService(AiExecutionLogRepository repository) {
        this(repository, false, false, false);
    }

    @Autowired
    public AiExecutionLogService(
        AiExecutionLogRepository repository,
        @Value("${app.ai.audit.enabled:false}") boolean auditEnabled,
        @Value("${app.ai.audit.raw-prompt-enabled:false}") boolean rawPromptEnabled,
        @Value("${app.ai.audit.raw-response-enabled:false}") boolean rawResponseEnabled
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.auditEnabled = auditEnabled;
        this.rawPromptEnabled = rawPromptEnabled;
        this.rawResponseEnabled = rawResponseEnabled;
    }

    /**
     * Log a TIER_1_RULES (deterministic, no LLM) execution.
     *
     * @param scanId    the saved scan
     * @param latencyMs rule-engine latency
     */
    public AiExecutionLog recordRulesOnly(Long scanId, long latencyMs) {
        if (!auditEnabled) {
            return null;
        }
        if (!validScanId(scanId) || !validLatency(latencyMs)) {
            LOGGER.warn("Skipped rules-only AI execution audit log because metadata was invalid.");
            return null;
        }

        AiExecutionLog log = baseLog(scanId, ExecutionTier.TIER_1_RULES, latencyMs);
        return saveSafely(log);
    }

    /**
     * Log a TIER_3_LLM execution, including the model, tokens, prompt and raw response.
     *
     * @param scanId the saved scan
     * @param tier   the tier used
     * @param llm    the LLM result carrying usage metadata
     */
    public AiExecutionLog record(Long scanId, ExecutionTier tier, LlmAssessmentResult llm) {
        if (!auditEnabled) {
            return null;
        }
        if (!validScanId(scanId) || tier == null || !validLlmResult(llm)) {
            LOGGER.warn("Skipped LLM AI execution audit log because metadata was invalid.");
            return null;
        }

        AiExecutionLog log = baseLog(scanId, tier, llm.latencyMs());
        log.setModelId(llm.modelId());
        log.setPromptTokens(llm.promptTokens());
        log.setCompletionTokens(llm.completionTokens());
        log.setCompiledPrompt(
            rawPromptEnabled ? redact(llm.compiledPrompt()) : null
        );
        log.setRawLlmResponse(
            rawResponseEnabled ? redact(llm.rawResponse()) : null
        );
        return saveSafely(log);
    }

    private AiExecutionLog baseLog(Long scanId, ExecutionTier tier, long latencyMs) {
        AiExecutionLog log = new AiExecutionLog();
        log.setScanId(scanId);
        log.setExecutionTier(tier.name());
        log.setLatencyMs((int) latencyMs);
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    private boolean validScanId(Long scanId) {
        return scanId != null && scanId > 0;
    }

    private boolean validLatency(long latencyMs) {
        return latencyMs >= 0 && latencyMs <= Integer.MAX_VALUE;
    }

    private boolean validLlmResult(LlmAssessmentResult llm) {
        return llm != null
            && validLatency(llm.latencyMs())
            && (llm.promptTokens() == null || llm.promptTokens() >= 0)
            && (llm.completionTokens() == null || llm.completionTokens() >= 0);
    }

    private String redact(String value) {
        if (value == null) {
            return null;
        }
        String withoutBearer = BEARER_TOKEN.matcher(value).replaceAll("Bearer [REDACTED]");
        return SENSITIVE_ASSIGNMENT.matcher(withoutBearer)
            .replaceAll("$1$2[REDACTED]");
    }

    private AiExecutionLog saveSafely(AiExecutionLog log) {
        try {
            return repository.save(log);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "AI execution audit persistence failed for scanId={}, errorType={}",
                log.getScanId(),
                exception.getClass().getSimpleName()
            );
            return null;
        }
    }
}
