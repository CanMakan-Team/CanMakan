package com.canmakan.backend.ai.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.product.assessment.ExecutionTier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests append-only audit mapping, redaction, and non-fatal persistence failure.
 *
 * @author YangMaowei
 */
class AiExecutionLogServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordsRulesOnlyExecution() {
        AiExecutionLogRepository repository = savingRepository();
        AiExecutionLogService service = new AiExecutionLogService(
                repository, objectMapper, false, false
        );

        AiExecutionLog log = service.recordRulesOnly(42L, 25L);

        assertEquals(42L, log.getScanId());
        assertEquals("TIER_1_RULES", log.getExecutionTier());
        assertEquals(25, log.getLatencyMs());
        assertNull(log.getModelId());
        assertNull(log.getCompiledPrompt());
        assertNull(log.getRawLlmResponse());
    }

    @Test
    void rawLoggingDisabledStoresSafeExecutionSummaries() throws Exception {
        AiExecutionLogService service = new AiExecutionLogService(
                savingRepository(), objectMapper, false, false
        );
        LlmAssessmentResult llm = result(
                LlmAssessmentResult.Status.SUCCESS,
                "{\"authorization\":\"Bearer secret-value\"}",
                "{\"answer\":\"secret raw response\"}",
                null
        );

        AiExecutionLog log = service.record(7L, ExecutionTier.TIER_3_LLM, llm);
        JsonNode prompt = objectMapper.readTree(log.getCompiledPrompt());
        JsonNode response = objectMapper.readTree(log.getRawLlmResponse());

        assertEquals("V1", prompt.path("promptVersion").asText());
        assertEquals("disabled", prompt.path("rawLogging").asText());
        assertEquals("SUCCESS", response.path("status").asText());
        assertEquals("disabled", response.path("rawLogging").asText());
        assertTrue(!log.getCompiledPrompt().contains("secret-value"));
        assertTrue(!log.getRawLlmResponse().contains("secret raw response"));
    }

    @Test
    void enabledRawLoggingRedactsSensitiveKeysAndValues() {
        AiExecutionLogService service = new AiExecutionLogService(
                savingRepository(), objectMapper, true, true
        );
        LlmAssessmentResult llm = result(
                LlmAssessmentResult.Status.SUCCESS,
                "{\"apiKey\":\"sk-secret123\",\"context\":\"Bearer abc.def\"}",
                "{\"answer\":\"Bearer abc.def\",\"password\":\"hidden\"}",
                null
        );

        AiExecutionLog log = service.record(7L, ExecutionTier.TIER_3_LLM, llm);

        assertTrue(log.getCompiledPrompt().contains("[REDACTED]"));
        assertTrue(log.getCompiledPrompt().contains("Bearer [REDACTED]"));
        assertTrue(log.getRawLlmResponse().contains("Bearer [REDACTED]"));
        assertTrue(log.getRawLlmResponse().contains("[REDACTED]"));
        assertTrue(!log.getCompiledPrompt().contains("sk-secret123"));
        assertTrue(!log.getRawLlmResponse().contains("hidden"));
    }

    @Test
    void recordsFailedModelExecutionStatusWithoutRawResponse() throws Exception {
        AiExecutionLogService service = new AiExecutionLogService(
                savingRepository(), objectMapper, false, false
        );
        LlmAssessmentResult llm = result(
                LlmAssessmentResult.Status.TIMEOUT,
                "{}",
                null,
                "The provider timed out."
        );

        AiExecutionLog log = service.record(9L, ExecutionTier.TIER_3_LLM, llm);
        JsonNode response = objectMapper.readTree(log.getRawLlmResponse());

        assertEquals("TIMEOUT", response.path("status").asText());
        assertEquals("The provider timed out.", response.path("error").asText());
    }

    @Test
    void persistenceFailureIsNonFatal() {
        AiExecutionLogRepository repository = mock(AiExecutionLogRepository.class);
        when(repository.save(any(AiExecutionLog.class)))
                .thenThrow(new IllegalStateException("database unavailable"));
        AiExecutionLogService service = new AiExecutionLogService(
                repository, objectMapper, false, false
        );

        assertNull(service.recordRulesOnly(42L, 5L));
        assertNull(service.record(
                42L,
                ExecutionTier.TIER_3_LLM,
                result(LlmAssessmentResult.Status.PROVIDER_ERROR, "{}", null, "failed")
        ));
    }

    private AiExecutionLogRepository savingRepository() {
        AiExecutionLogRepository repository = mock(AiExecutionLogRepository.class);
        when(repository.save(any(AiExecutionLog.class))).thenAnswer(invocation -> {
            AiExecutionLog log = invocation.getArgument(0);
            assertSame(log, invocation.getArgument(0));
            return log;
        });
        return repository;
    }

    private LlmAssessmentResult result(
            LlmAssessmentResult.Status status,
            String prompt,
            String response,
            String error
    ) {
        return new LlmAssessmentResult(
                List.of(),
                List.of("Unknown"),
                Map.of(),
                new BigDecimal("0.5"),
                "Evidence only.",
                "test-model",
                10,
                5,
                15,
                123L,
                "V1",
                "corr-1",
                List.of("tool summary"),
                prompt,
                response,
                status,
                error
        );
    }
}
