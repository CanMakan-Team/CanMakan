package com.canmakan.backend.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpTimeoutException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * Tests evidence parsing through a mocked Spring AI ChatClient boundary without network access.
 *
 * @author YangMaowei
 * @author Amelia
 */
@DisplayName("UC3: LlmClient - Parses evidence JSON and captures model and token metadata")
@ExtendWith(MockitoExtension.class)
class LlmClientTest {

    private static final String COMPILED_PROMPT = "evidence prompt";

    @Mock
    private ChatClient chatClient;

    private ChatClient.CallResponseSpec callResponseSpec;
    private LlmClient client;

    private ObjectMapper objectMapper;

    @BeforeEach
    void createEnabledClient() {
        objectMapper = new ObjectMapper();
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        Mockito.lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        Mockito.lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        Mockito.lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
        // Force JSON fallback path in unit tests (entity conversion needs a live Spring AI stack).
        Mockito.lenient()
                .when(callResponseSpec.entity(eq(EvidencePayload.class)))
                .thenThrow(new RuntimeException("force fallback parse"));
        client = new LlmClient(chatClient, objectMapper, true);
    }

    @Test
    void disabledClientDoesNotCallProvider() {
        LlmClient disabledClient = new LlmClient(chatClient, new ObjectMapper(), false);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> disabledClient.assess(COMPILED_PROMPT)
        );

        assertEquals("AI assessment is disabled.", exception.getMessage());
        verifyNoInteractions(chatClient);
    }

    @Test
    void parsesMultipleResolvedAndUnresolvedIngredients() throws Exception {
        String rawResponse = """
                {
                    "resolvedIngredients": [
                        {"ingredientName":"Milk","rootAllergen":"DAIRY","confidence":0.95},
                        {"ingredientName":"Mystery additive","rootAllergen":null,"confidence":0.25}
                    ],
                    "analysisNotes":"Second ingredient remains unresolved."
                }
                """;
        stubResponse(rawResponse, null);

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
        assertJsonEquals(rawResponse, result.rawResponse());
        assertTrue(result.latencyMs() >= 0);
        verify(chatClient).prompt();
    }

    @Test
    void capturesAvailableModelAndTokenMetadata() {
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(metadata.getModel()).thenReturn("test-model");
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(12);
        when(usage.getCompletionTokens()).thenReturn(7);
        stubResponse(validResponse(), metadata);

        LlmAssessmentResult result = client.assess(COMPILED_PROMPT);

        assertEquals("test-model", result.modelId());
        assertEquals(12, result.promptTokens());
        assertEquals(7, result.completionTokens());
    }

    @Test
    void leavesUnavailableMetadataNull() {
        stubResponse(validResponse(), null);

        LlmAssessmentResult result = client.assess(COMPILED_PROMPT);

        assertNull(result.modelId());
        assertNull(result.promptTokens());
        assertNull(result.completionTokens());
    }

    @Test
    void rejectsMalformedJsonAndMissingResolvedIngredients() {
        stubResponse("{not-json", null);
        assertInvalidOutput();

        stubResponse("{\"analysisNotes\":\"missing list\"}", null);
        assertInvalidOutput();
    }

    @Test
    void rejectsNullListElement() {
        stubResponse(
                "{\"resolvedIngredients\":[null],\"analysisNotes\":\"\"}",
                null
        );

        assertInvalidOutput();
    }

    @Test
    void rejectsStructurallyInvalidEvidencePayloads() {
        List<String> invalidPayloads = List.of(
                // resolvedIngredients present but not an array
                "{\"resolvedIngredients\":\"not-an-array\",\"analysisNotes\":\"\"}",
                // array element present but not an object
                "{\"resolvedIngredients\":[\"not-an-object\"],\"analysisNotes\":\"\"}",
                // ingredientName key missing entirely
                "{\"resolvedIngredients\":[{\"rootAllergen\":null,\"confidence\":0.5}],"
                        + "\"analysisNotes\":\"\"}",
                // ingredientName present but not textual
                "{\"resolvedIngredients\":[{\"ingredientName\":1,\"rootAllergen\":null,"
                        + "\"confidence\":0.5}],\"analysisNotes\":\"\"}",
                // rootAllergen key missing entirely (distinct from an explicit JSON null)
                "{\"resolvedIngredients\":[{\"ingredientName\":\"A\",\"confidence\":0.5}],"
                        + "\"analysisNotes\":\"\"}",
                // confidence key missing entirely (distinct from a non-numeric value)
                "{\"resolvedIngredients\":[{\"ingredientName\":\"A\",\"rootAllergen\":null}],"
                        + "\"analysisNotes\":\"\"}",
                // rootAllergen present but neither null nor textual
                "{\"resolvedIngredients\":[{\"ingredientName\":\"A\",\"rootAllergen\":1,"
                        + "\"confidence\":0.5}],\"analysisNotes\":\"\"}",
                // analysisNotes present but not textual
                "{\"resolvedIngredients\":[],\"analysisNotes\":1}"
        );

        for (String payload : invalidPayloads) {
            stubResponse(payload, null);
            assertInvalidOutput();
        }
    }

    @Test
    void treatsMissingOrExplicitNullAnalysisNotesAsEmpty() {
        stubResponse("{\"resolvedIngredients\":[]}", null);
        assertEquals("", client.assess(COMPILED_PROMPT).analysisNotes());

        stubResponse("{\"resolvedIngredients\":[],\"analysisNotes\":null}", null);
        assertEquals("", client.assess(COMPILED_PROMPT).analysisNotes());
    }

    @Test
    void fallsBackToChatResponseOutputTextWhenContentAccessorIsBlank() {
        ChatResponse response = mock(ChatResponse.class);
        Mockito.lenient().when(response.getMetadata()).thenReturn(null);
        Generation generation = mock(Generation.class);
        AssistantMessage message = mock(AssistantMessage.class);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn(validResponse());
        when(response.getResult()).thenReturn(generation);
        when(callResponseSpec.content()).thenReturn(null);
        when(callResponseSpec.chatResponse()).thenReturn(response);

        LlmAssessmentResult result = client.assess(COMPILED_PROMPT);

        assertEquals(1, result.resolvedIngredients().size());
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void rejectsOutOfRangeAndNonNumericConfidence() {
        for (String confidence : List.of("-0.01", "1.01", "\"NaN\"", "\"Infinity\"")) {
            stubResponse(responseWithConfidence(confidence), null);
            assertInvalidOutput();
        }
    }

    @Test
    void rejectsEmptyOrMissingProviderContent() {
        stubResponse(" ", null);
        assertInvalidOutput();
        // Blank content triggers one retry, so prompt is used twice per assess.
        verify(chatClient, times(2)).prompt();

        when(callResponseSpec.content()).thenReturn(null);
        when(callResponseSpec.chatResponse()).thenReturn(null);
        assertInvalidOutput();
    }

    @Test
    void retriesOnceWhenFirstProviderContentIsBlank() {
        ChatResponse response = mock(ChatResponse.class);
        Mockito.lenient().when(response.getMetadata()).thenReturn(null);
        when(callResponseSpec.content())
                .thenReturn("")
                .thenReturn(validResponse());
        when(callResponseSpec.chatResponse()).thenReturn(response);

        LlmAssessmentResult result = client.assess(COMPILED_PROMPT);

        assertEquals(1, result.resolvedIngredients().size());
        assertEquals("DAIRY", result.resolvedIngredients().getFirst().rootAllergen());
        verify(chatClient, times(2)).prompt();
    }

    @Test
    void providerFailureIsControlledNotRetriedAndDoesNotExposeSecret() {
        when(chatClient.prompt())
                .thenThrow(new RuntimeException("Authorization: Bearer private-key"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.assess(COMPILED_PROMPT)
        );

        assertEquals("AI provider request failed.", exception.getMessage());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains("private-key"));
    }

    @Test
    void timeoutLikeFailureIsControlledAndNotRetried() {
        when(chatClient.prompt()).thenThrow(
                new RuntimeException(new HttpTimeoutException("timed out"))
        );

        assertThrows(IllegalStateException.class, () -> client.assess(COMPILED_PROMPT));
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
        verify(chatClient, never()).prompt();
    }

    @Test
    void stripsMarkdownFencesAroundEvidenceJson() {
        stubResponse("""
                ```json
                {"resolvedIngredients":[],"analysisNotes":"ok"}
                ```
                """, null);

        LlmAssessmentResult result = client.assess(COMPILED_PROMPT);

        assertEquals(List.of(), result.resolvedIngredients());
        assertEquals("ok", result.analysisNotes());
    }

    private void stubResponse(String rawResponse, ChatResponseMetadata metadata) {
        ChatResponse response = mock(ChatResponse.class);
        // Metadata is only read after a successful parse; invalid JSON never touches it.
        Mockito.lenient().when(response.getMetadata()).thenReturn(metadata);
        when(callResponseSpec.content()).thenReturn(rawResponse);
        when(callResponseSpec.chatResponse()).thenReturn(response);
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

    private void assertJsonEquals(String expectedJson, String actualJson) throws Exception {
        JsonNode expected = objectMapper.readTree(expectedJson);
        JsonNode actual = objectMapper.readTree(actualJson);
        assertEquals(expected, actual);
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
