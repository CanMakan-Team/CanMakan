package com.canmakan.backend.ai.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.canmakan.backend.ai.llm.LlmAssessmentResult;
import com.canmakan.backend.product.assessment.ExecutionTier;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests AI execution audit mapping and failure isolation without JPA.
 *
 * @author YangMaowei
 */
@ExtendWith(MockitoExtension.class)
class AiExecutionLogServiceTest {

    @Mock
    private AiExecutionLogRepository repository;

    @BeforeEach
    void returnSavedEntity() {
        lenient().when(repository.save(any(AiExecutionLog.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordsRulesOnlyTierScanLatencyAndTimestampOnce() {
        AiExecutionLogService service = service(true, false, false);
        LocalDateTime before = LocalDateTime.now();

        AiExecutionLog saved = service.recordRulesOnly(42L, 17L);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<AiExecutionLog> captor = ArgumentCaptor.forClass(AiExecutionLog.class);
        verify(repository).save(captor.capture());
        assertSame(captor.getValue(), saved);
        assertEquals(42L, saved.getScanId());
        assertEquals(ExecutionTier.TIER_1_RULES.name(), saved.getExecutionTier());
        assertEquals(17, saved.getLatencyMs());
        assertNull(saved.getModelId());
        assertNull(saved.getPromptTokens());
        assertNull(saved.getCompletionTokens());
        assertNull(saved.getCompiledPrompt());
        assertNull(saved.getRawLlmResponse());
        assertFalse(saved.getCreatedAt().isBefore(before));
        assertFalse(saved.getCreatedAt().isAfter(after));
    }

    @Test
    void mapsLlmMetadataWithoutChangingTheSourceResult() {
        AiExecutionLogService service = service(true, false, false);
        LlmAssessmentResult llm = llmResult("prompt", "response");
        LlmAssessmentResult original = llm;

        AiExecutionLog saved = service.record(42L, ExecutionTier.TIER_3_LLM, llm);

        assertEquals(ExecutionTier.TIER_3_LLM.name(), saved.getExecutionTier());
        assertEquals("test-model", saved.getModelId());
        assertEquals(12, saved.getPromptTokens());
        assertEquals(7, saved.getCompletionTokens());
        assertEquals(35, saved.getLatencyMs());
        assertEquals(original, llm);
        verify(repository).save(saved);
    }

    @Test
    void omitsRawPromptAndResponseWhenSwitchesAreDisabled() {
        AiExecutionLogService service = service(true, false, false);

        AiExecutionLog saved = service.record(
            42L,
            ExecutionTier.TIER_3_LLM,
            llmResult("private prompt", "private response")
        );

        assertNull(saved.getCompiledPrompt());
        assertNull(saved.getRawLlmResponse());
    }

    @Test
    void storesEnabledRawFieldsAfterRedaction() {
        AiExecutionLogService service = service(true, true, true);
        LlmAssessmentResult llm = llmResult(
            "Authorization: Bearer prompt-secret\napi_key=sk-private",
            "{\"token\":\"response-secret\",\"evidence\":\"ok\"}"
        );

        AiExecutionLog saved = service.record(42L, ExecutionTier.TIER_3_LLM, llm);

        assertTrue(saved.getCompiledPrompt().contains("[REDACTED]"));
        assertTrue(saved.getRawLlmResponse().contains("[REDACTED]"));
        assertFalse(saved.getCompiledPrompt().contains("prompt-secret"));
        assertFalse(saved.getCompiledPrompt().contains("sk-private"));
        assertFalse(saved.getRawLlmResponse().contains("response-secret"));
        assertTrue(saved.getRawLlmResponse().contains("\"evidence\":\"ok\""));
    }

    @Test
    void preservesEnabledRawFieldsWhenTheyContainNoSecrets() {
        AiExecutionLogService service = service(true, true, true);

        AiExecutionLog saved = service.record(
            42L,
            ExecutionTier.TIER_3_LLM,
            llmResult("evidence prompt", "{\"evidence\":\"uncertain\"}")
        );

        assertEquals("evidence prompt", saved.getCompiledPrompt());
        assertEquals("{\"evidence\":\"uncertain\"}", saved.getRawLlmResponse());
    }

    @Test
    void isolatesRepositoryFailureFromTheBusinessFlow() {
        AiExecutionLogService service = service(true, false, false);
        when(repository.save(any(AiExecutionLog.class)))
            .thenThrow(new RuntimeException("password=do-not-log"));

        AiExecutionLog result = service.recordRulesOnly(42L, 17L);

        assertNull(result);
        verify(repository).save(any(AiExecutionLog.class));
    }

    @Test
    void skipsPersistenceWhenAuditIsDisabled() {
        AiExecutionLogService service = service(false, true, true);

        assertNull(service.recordRulesOnly(42L, 17L));
        assertNull(service.record(42L, ExecutionTier.TIER_3_LLM, llmResult("p", "r")));
        verifyNoInteractions(repository);
    }

    @Test
    void handlesNullAndInvalidMetadataWithoutCallingRepository() {
        AiExecutionLogService service = service(true, false, false);

        assertNull(service.recordRulesOnly(null, 17L));
        assertNull(service.recordRulesOnly(42L, -1L));
        assertNull(service.record(42L, null, llmResult("p", "r")));
        assertNull(service.record(42L, ExecutionTier.TIER_3_LLM, null));
        assertNull(service.record(
            42L,
            ExecutionTier.TIER_3_LLM,
            new LlmAssessmentResult(null, null, null, -1, null, 1L, null, null)
        ));

        verify(repository, never()).save(any(AiExecutionLog.class));
    }

    private AiExecutionLogService service(
        boolean auditEnabled,
        boolean rawPromptEnabled,
        boolean rawResponseEnabled
    ) {
        return new AiExecutionLogService(
            repository,
            auditEnabled,
            rawPromptEnabled,
            rawResponseEnabled
        );
    }

    private static LlmAssessmentResult llmResult(String prompt, String response) {
        return new LlmAssessmentResult(
            null,
            "Evidence remains uncertain.",
            "test-model",
            12,
            7,
            35L,
            prompt,
            response
        );
    }
}
