package com.canmakan.backend.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Tests structured parsing and controlled model failure handling with test doubles.
 *
 * @author YangMaowei
 */
class LlmClientTest {

    private static final String COMPILED_REQUEST = """
            {"promptVersion":"CANMAKAN-EVIDENCE-V1","correlationId":"corr-1"}
            """;

    @Test
    void parsesEvidenceAndCapturesUsageWithoutFinalVerdict() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = successResponse();
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        LlmClient client = client(chatModel, Duration.ofSeconds(1), 0);

        try {
            LlmAssessmentResult result = client.assess(COMPILED_REQUEST);

            assertTrue(result.successful());
            assertEquals("model-test", result.modelId());
            assertEquals(11, result.inputTokens());
            assertEquals(7, result.outputTokens());
            assertEquals(18, result.totalTokens());
            assertEquals("corr-1", result.correlationId());
            assertEquals("Unknown Additive", result.unresolvedIngredients().getFirst());
            assertEquals("E471", result.resolvedNames().get("Unknown Additive"));
            assertEquals("VEGAN", result.proposedFindings().getFirst().restrictionCode());
            assertEquals("MODEL_EVIDENCE", result.proposedFindings().getFirst().type().name());
            assertNull(result.errorMessage());
        } finally {
            client.closeExecutor();
        }
    }

    @Test
    void invalidJsonReturnsControlledInvalidResponse() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = responseWithText("not-json");
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        LlmClient client = client(chatModel, Duration.ofSeconds(1), 0);

        try {
            LlmAssessmentResult result = client.assess(COMPILED_REQUEST);

            assertEquals(LlmAssessmentResult.Status.INVALID_RESPONSE, result.status());
            assertEquals("not-json", result.rawResponse());
        } finally {
            client.closeExecutor();
        }
    }

    @Test
    void transientProviderFailureIsRetriedWithinBound() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = successResponse();
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new ResourceAccessException("temporary"))
                .thenReturn(response);
        LlmClient client = client(chatModel, Duration.ofSeconds(1), 1);

        try {
            assertTrue(client.assess(COMPILED_REQUEST).successful());
            verify(chatModel, times(2)).call(any(Prompt.class));
        } finally {
            client.closeExecutor();
        }
    }

    @Test
    void timeoutReturnsControlledStatus() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = successResponse();
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(500);
            return response;
        });
        LlmClient client = client(chatModel, Duration.ofMillis(10), 0);

        try {
            assertEquals(
                    LlmAssessmentResult.Status.TIMEOUT,
                    client.assess(COMPILED_REQUEST).status()
            );
        } finally {
            client.closeExecutor();
        }
    }

    @Test
    void toolFailureReturnsControlledStatus() {
        ChatModel chatModel = mock(ChatModel.class);
        ToolExecutionException toolFailure = mock(ToolExecutionException.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(toolFailure);
        LlmClient client = client(chatModel, Duration.ofSeconds(1), 0);

        try {
            assertEquals(
                    LlmAssessmentResult.Status.TOOL_ERROR,
                    client.assess(COMPILED_REQUEST).status()
            );
        } finally {
            client.closeExecutor();
        }
    }

    @Test
    void unavailableProviderReturnsControlledStatus() {
        LlmClient client = client(null, Duration.ofSeconds(1), 0);

        try {
            assertEquals(
                    LlmAssessmentResult.Status.PROVIDER_UNAVAILABLE,
                    client.assess(COMPILED_REQUEST).status()
            );
        } finally {
            client.closeExecutor();
        }
    }

    private static LlmClient client(ChatModel chatModel, Duration timeout, int retryCount) {
        return new LlmClient(
                chatModel,
                new ObjectMapper(),
                timeout,
                retryCount,
                Duration.ZERO,
                "configured-model",
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    private static ChatResponse successResponse() {
        return responseWithText("""
                {
                  "proposedFindings": [{
                    "restrictionCode": "VEGAN",
                    "ingredientName": "Unknown Additive",
                    "reason": "The origin remains ambiguous."
                  }],
                  "unresolvedIngredients": ["Unknown Additive"],
                  "resolvedNames": {"Unknown Additive": "E471"},
                  "confidence": 0.65,
                  "explanation": "Additional evidence is required.",
                  "toolCalls": ["ingredient_alias_lookup completed"]
                }
                """);
    }

    private static ChatResponse responseWithText(String text) {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = mock(AssistantMessage.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);

        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn(text);
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getModel()).thenReturn("model-test");
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(11);
        when(usage.getCompletionTokens()).thenReturn(7);
        when(usage.getTotalTokens()).thenReturn(18);
        when(response.hasToolCalls()).thenReturn(false);
        return response;
    }
}
