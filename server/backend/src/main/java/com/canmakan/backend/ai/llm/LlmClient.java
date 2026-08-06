package com.canmakan.backend.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Tool-calling evidence agent over Spring AI {@link ChatClient}.
 * The model may invoke dietary knowledge tools mid-reasoning; the final text
 * must still be evidence JSON only (no SAFE/WARNING/UNSAFE verdict).
 *
 * @author XieHuayuan
 * @author YangMaowei
 * @author Amelia
 */
@Service
public class LlmClient {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    @Autowired
    public LlmClient(
            @Qualifier("dietaryEvidenceChatClient")
            ChatClient dietaryEvidenceChatClient,
            @Value("${canmakan.ai.enabled:false}") boolean enabled
    ) {
        this(dietaryEvidenceChatClient, new ObjectMapper(), enabled);
    }

    /**
     * Package-visible constructor for unit tests.
     */
    LlmClient(ChatClient chatClient, ObjectMapper objectMapper, boolean enabled) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.enabled = enabled;
    }

    /**
     * Run one LLM assessment with autonomous tool use.
     *
     * @param compiledPrompt the prompt built by {@link PromptBuilder}
     * @return structured ingredient evidence plus usage metadata
     */
    public LlmAssessmentResult assess(String compiledPrompt) {
        Objects.requireNonNull(compiledPrompt, "compiledPrompt must not be null");
        if (compiledPrompt.isBlank()) {
            throw new IllegalArgumentException("compiledPrompt must not be blank");
        }
        if (!enabled) {
            throw new IllegalStateException("AI assessment is disabled.");
        }

        long startedAt = System.nanoTime();
        ChatResponse response;
        String rawResponse;
        try {
            var call = chatClient.prompt()
                    .user(compiledPrompt)
                    .call();
            response = call.chatResponse();
            rawResponse = call.content();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("AI provider request failed.");
        }

        if (rawResponse == null || rawResponse.isBlank()) {
            throw invalidProviderOutput();
        }

        List<ResolvedIngredient> resolvedIngredients = parseResolvedIngredients(rawResponse);
        String analysisNotes = parseAnalysisNotes(rawResponse);
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;

        ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();

        return new LlmAssessmentResult(
            resolvedIngredients,
            analysisNotes,
            metadata == null ? null : blankToNull(metadata.getModel()),
            usage == null ? null : usage.getPromptTokens(),
            usage == null ? null : usage.getCompletionTokens(),
            latencyMs,
            compiledPrompt,
            rawResponse
        );
    }

    private List<ResolvedIngredient> parseResolvedIngredients(String rawResponse) {
        JsonNode root = parseRoot(rawResponse);
        JsonNode ingredientsNode = root.get("resolvedIngredients");
        if (ingredientsNode == null || !ingredientsNode.isArray()) {
            throw invalidProviderOutput();
        }

        List<ResolvedIngredient> resolvedIngredients = new ArrayList<>();
        for (JsonNode ingredientNode : ingredientsNode) {
            if (ingredientNode == null || !ingredientNode.isObject()) {
                throw invalidProviderOutput();
            }

            JsonNode nameNode = ingredientNode.get("ingredientName");
            JsonNode rootAllergenNode = ingredientNode.get("rootAllergen");
            JsonNode confidenceNode = ingredientNode.get("confidence");
            if (nameNode == null || !nameNode.isTextual()
                    || rootAllergenNode == null
                    || confidenceNode == null || !confidenceNode.isNumber()) {
                throw invalidProviderOutput();
            }

            String rootAllergen;
            if (rootAllergenNode.isNull()) {
                rootAllergen = null;
            } else if (rootAllergenNode.isTextual()) {
                rootAllergen = rootAllergenNode.textValue();
            } else {
                throw invalidProviderOutput();
            }

            try {
                resolvedIngredients.add(new ResolvedIngredient(
                    nameNode.textValue(),
                    rootAllergen,
                    confidenceNode.doubleValue()
                ));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw invalidProviderOutput();
            }
        }
        return List.copyOf(resolvedIngredients);
    }

    private String parseAnalysisNotes(String rawResponse) {
        JsonNode notesNode = parseRoot(rawResponse).get("analysisNotes");
        if (notesNode == null || notesNode.isNull()) {
            return "";
        }
        if (!notesNode.isTextual()) {
            throw invalidProviderOutput();
        }
        return notesNode.textValue();
    }

    private JsonNode parseRoot(String rawResponse) {
        try {
            // Models sometimes wrap JSON in markdown fences; strip a simple fence if present.
            String trimmed = rawResponse.trim();
            if (trimmed.startsWith("```")) {
                int firstNl = trimmed.indexOf('\n');
                int lastFence = trimmed.lastIndexOf("```");
                if (firstNl > 0 && lastFence > firstNl) {
                    trimmed = trimmed.substring(firstNl + 1, lastFence).trim();
                }
            }
            JsonNode root = objectMapper.readTree(trimmed);
            if (root == null || !root.isObject()) {
                throw invalidProviderOutput();
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw invalidProviderOutput();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private IllegalArgumentException invalidProviderOutput() {
        return new IllegalArgumentException("AI provider returned invalid structured evidence.");
    }
}
