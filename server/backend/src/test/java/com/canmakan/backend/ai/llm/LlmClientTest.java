package com.canmakan.backend.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpTimeoutException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Tests evidence parsing through a mocked Spring AI boundary without network access.
 *
 * @author YangMaowei
 */
@ExtendWith(MockitoExtension.class)
class LlmClientTest {

    private static final String COMPILED_PROMPT = "evidence prompt";

    @Mock
    private ChatModel chatModel;

    private LlmClient client;

    @BeforeEach
    void createEnabledClient() {
        client = new LlmClient(chatModel, new ObjectMapper(), true);
    }

    @Test
    void disabledClientDoesNotCallProvider() {
        LlmClient disabledClient = new LlmClient(chatModel, new ObjectMapper(), false);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> disabledClient.assess(COMPILED_PROMPT)
        );

        assertEquals("AI assessment is disabled.", exception.getMessage());
        verifyNoInteractions(chatModel);
    }

    @Test
    void parsesMultipleResolvedAndUnresolvedIngredients() {
        String rawResponse = """
                {
                  "resolvedIngredients": [
                    {"ingredientName":"Milk","rootAllergen":"DAIRY","confidence":0.95},
                    {"ingredientName":"Mystery additive","rootAllergen":null,"confidence":0.25}
                  ],
                  "analysisNotes":"Second ingredient remains unresolved."
                }
                """;
        stubResponse(rawResponse, null, null);

        LlmAssessmentResult result = client.assess(COMPILED_PROMPT);

        assertEquals(2, result.resolvedIngredients().size());
        assertEquals(
                new ResolvedIngredient("Milk", "DAIRY", 0.95),
                result.resolvedIngredients().getFirst()
        );
        assertNull(result.resolvedIngredients().get(1).rootAllergen());
        assertEquals(0.25, result.resolvedIngredients().get(1).confidence());
        assertEquals("Second ingredient remains unresolved.", result.analysisNotes());
        assertEquals(COMPILED_PROMPT, result.compiledPrompt());
        assertEquals(rawResponse, result.rawResponse());
        assertTrue(result.latencyMs() >= 0);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertEquals(COMPILED_PROMPT, promptCaptor.getValue().getContents());
    }

    @Test
    void capturesAvailableModelAndTokenMetadata() {
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(metadata.getModel()).thenReturn("test-model");
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(12);
        when(usage.getCompletionTokens()).thenReturn(7);
        stubResponse(validResponse(), metadata, null);

        LlmAssessmentResult result = client.assess(COMPILED_PROMPT);

        assertEquals("test-model", result.modelId());
        assertEquals(12, result.promptTokens());
        assertEquals(7, result.completionTokens());
    }

    @Test
    void leavesUnavailableMetadataNull() {
        stubResponse(validResponse(), null, null);

        LlmAssessmentResult result = client.assess(COMPILED_PROMPT);

        assertNull(result.modelId());
        assertNull(result.promptTokens());
        assertNull(result.completionTokens());
    }

    @Test
    void rejectsMalformedJsonAndMissingResolvedIngredients() {
        stubResponse("{not-json", null, null);
        assertInvalidOutput();

        stubResponse("{\"analysisNotes\":\"missing list\"}", null, null);
        assertInvalidOutput();
    }

    @Test
    void rejectsNullListElement() {
        stubResponse(
                "{\"resolvedIngredients\":[null],\"analysisNotes\":\"\"}",
                null,
                null
        );

        assertInvalidOutput();
    }

    @Test
    void rejectsOutOfRangeAndNonNumericConfidence() {
        for (String confidence : List.of("-0.01", "1.01", "\"NaN\"", "\"Infinity\"")) {
            stubResponse(responseWithConfidence(confidence), null, null);
            assertInvalidOutput();
        }
    }

    @Test
    void rejectsEmptyOrMissingProviderContent() {
        stubResponse(" ", null, null);
        assertInvalidOutput();

        when(chatModel.call(any(Prompt.class))).thenReturn(null);
        assertInvalidOutput();
    }

    @Test
    void providerFailureIsControlledNotRetriedAndDoesNotExposeSecret() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("Authorization: Bearer private-key"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.assess(COMPILED_PROMPT)
        );

        assertEquals("AI provider request failed.", exception.getMessage());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains("private-key"));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void timeoutLikeFailureIsControlledAndNotRetried() {
        when(chatModel.call(any(Prompt.class))).thenThrow(
                new RuntimeException(new HttpTimeoutException("timed out"))
        );

        assertThrows(IllegalStateException.class, () -> client.assess(COMPILED_PROMPT));

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void ignoresProviderVerdictAndReasonFields() {
        stubResponse(
                """
                {
                  "resolvedIngredients": [],
                  "analysisNotes": "Evidence only.",
                  "verdict": "UNSAFE",
                  "reason": "Provider attempted a verdict."
                }
                """,
                null,
                null
        );

        LlmAssessmentResult result = client.assess(COMPILED_PROMPT);

        assertEquals(List.of(), result.resolvedIngredients());
        assertEquals("Evidence only.", result.analysisNotes());
        assertThrows(
                NoSuchMethodException.class,
                () -> result.getClass().getMethod("verdict")
        );
        assertThrows(
                NoSuchMethodException.class,
                () -> result.getClass().getMethod("reason")
        );
    }

    @Test
    void exposesEvidenceResultRatherThanSafetyVerdict() throws NoSuchMethodException {
        assertEquals(
                LlmAssessmentResult.class,
                LlmClient.class.getMethod("assess", String.class).getReturnType()
        );
    }

    @Test
    void rejectsNullAndBlankPromptWithoutCallingProvider() {
        assertThrows(NullPointerException.class, () -> client.assess(null));
        assertThrows(IllegalArgumentException.class, () -> client.assess("  "));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    private void stubResponse(
            String rawResponse,
            ChatResponseMetadata metadata,
            Generation suppliedGeneration
    ) {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = suppliedGeneration == null ? mock(Generation.class) : suppliedGeneration;
        AssistantMessage message = mock(AssistantMessage.class);
        when(message.getText()).thenReturn(rawResponse);
        when(generation.getOutput()).thenReturn(message);
        when(response.getResult()).thenReturn(generation);
        lenient().when(response.getMetadata()).thenReturn(metadata);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    private void assertInvalidOutput() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> client.assess(COMPILED_PROMPT)
        );
        assertEquals(
                "AI provider returned invalid structured evidence.",
                exception.getMessage()
        );
    }

    private static String validResponse() {
        return """
                {
                  "resolvedIngredients": [
                    {"ingredientName":"Milk","rootAllergen":"DAIRY","confidence":0.95}
                  ],
                  "analysisNotes":"Evidence only."
                }
                """;
    }

    private static String responseWithConfidence(String confidence) {
        return """
                {"resolvedIngredients":[{"ingredientName":"A","rootAllergen":null,
                "confidence":%s}],"analysisNotes":""}
                """.formatted(confidence);
    }
}
