package com.canmakan.backend.ai.log;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.product.assessment.ExecutionTier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Appends redacted assessment execution records to the audit table.
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
@Service
public class AiExecutionLogService {

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(authorization|api[-_]?key|password|secret|cookie|jwt|token).*"
    );
    private static final Pattern BEARER_VALUE = Pattern.compile(
            "(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+"
    );
    private static final Pattern JWT_VALUE = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"
    );
    private static final Pattern PROVIDER_KEY_VALUE = Pattern.compile(
            "\\bsk-[A-Za-z0-9_-]+\\b"
    );

    private final AiExecutionLogRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean logRawPrompt;
    private final boolean logRawResponse;

    public AiExecutionLogService(
            AiExecutionLogRepository repository,
            ObjectMapper objectMapper,
            @Value("${app.ai.audit.log-raw-prompt:false}") boolean logRawPrompt,
            @Value("${app.ai.audit.log-raw-response:false}") boolean logRawResponse
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.logRawPrompt = logRawPrompt;
        this.logRawResponse = logRawResponse;
    }

    public AiExecutionLog recordRulesOnly(Long scanId, long latencyMs) {
        if (scanId == null) {
            return null;
        }

        AiExecutionLog log = baseLog(scanId, ExecutionTier.TIER_1_RULES, latencyMs);
        return safeSave(log);
    }

    public AiExecutionLog record(Long scanId, ExecutionTier tier, LlmAssessmentResult llm) {
        if (scanId == null || tier == null || llm == null) {
            return null;
        }

        AiExecutionLog log = baseLog(scanId, tier, llm.latencyMs());
        log.setModelId(llm.modelId());
        log.setPromptTokens(llm.inputTokens());
        log.setCompletionTokens(llm.outputTokens());
        log.setCompiledPrompt(compiledPromptForAudit(llm));
        log.setRawLlmResponse(responseForAudit(llm));
        return safeSave(log);
    }

    private AiExecutionLog baseLog(Long scanId, ExecutionTier tier, long latencyMs) {
        AiExecutionLog log = new AiExecutionLog();
        log.setScanId(scanId);
        log.setExecutionTier(tier.name());
        log.setLatencyMs(toDatabaseInteger(latencyMs));
        return log;
    }

    private String compiledPromptForAudit(LlmAssessmentResult llm) {
        if (logRawPrompt && llm.compiledPrompt() != null) {
            return redactJson(llm.compiledPrompt());
        }

        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("promptVersion", llm.promptVersion());
        summary.put("correlationId", llm.correlationId());
        summary.put("rawLogging", "disabled");
        return writeJson(summary);
    }

    private String responseForAudit(LlmAssessmentResult llm) {
        if (logRawResponse && llm.rawResponse() != null) {
            return redactJson(llm.rawResponse());
        }

        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("status", llm.status().name());
        if (llm.errorMessage() != null) {
            summary.put("error", redactText(llm.errorMessage()));
        }
        ArrayNode toolCalls = summary.putArray("toolCalls");
        llm.toolCallSummary().forEach(toolCall -> toolCalls.add(redactText(toolCall)));
        summary.put("rawLogging", "disabled");
        return writeJson(summary);
    }

    private String redactJson(String content) {
        try {
            JsonNode parsed = objectMapper.readTree(content);
            return writeJson(redactNode(parsed));
        } catch (JsonProcessingException exception) {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.put("redactedText", redactText(content));
            return writeJson(wrapper);
        }
    }

    private JsonNode redactNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SENSITIVE_KEY.matcher(field.getKey()).matches()) {
                    result.put(field.getKey(), "[REDACTED]");
                } else {
                    result.set(field.getKey(), redactNode(field.getValue()));
                }
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(value -> result.add(redactNode(value)));
            return result;
        }
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(redactText(node.asText()));
        }
        return node.deepCopy();
    }

    private String redactText(String value) {
        if (value == null) {
            return null;
        }
        String redacted = BEARER_VALUE.matcher(value).replaceAll("Bearer [REDACTED]");
        redacted = JWT_VALUE.matcher(redacted).replaceAll("[REDACTED-JWT]");
        return PROVIDER_KEY_VALUE.matcher(redacted).replaceAll("[REDACTED-KEY]");
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            return "{\"status\":\"serialization-failed\"}";
        }
    }

    private Integer toDatabaseInteger(long latencyMs) {
        if (latencyMs < 0) {
            return 0;
        }
        return latencyMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) latencyMs;
    }

    private AiExecutionLog safeSave(AiExecutionLog log) {
        try {
            return repository.save(log);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
