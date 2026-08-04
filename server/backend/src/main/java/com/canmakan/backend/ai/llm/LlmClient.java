package com.canmakan.backend.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper over Spring AI's {@link ChatModel}. It parses evidence-oriented
 * ingredient resolution output and captures provider metadata for the audit log.
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
@Service
public class LlmClient {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    @Autowired
    public LlmClient(
            ChatModel chatModel,
            @Value("${canmakan.ai.enabled:false}") boolean enabled
    ) {
        this(chatModel, new ObjectMapper(), enabled);
    }

    LlmClient(ChatModel chatModel, ObjectMapper objectMapper, boolean enabled) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.enabled = enabled;
    }

    /**
     * Run one LLM assessment.
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
        try {
            response = chatModel.call(new Prompt(compiledPrompt));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("AI provider request failed.");
        }

        String rawResponse = responseText(response);
        List<ResolvedIngredient> resolvedIngredients = parseResolvedIngredients(rawResponse);
        String analysisNotes = parseAnalysisNotes(rawResponse);
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;

        ChatResponseMetadata metadata = response.getMetadata();
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

    private String responseText(ChatResponse response) {
        if (response == null) {
            throw invalidProviderOutput();
        }
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            throw invalidProviderOutput();
        }
        String text = generation.getOutput().getText();
        if (text == null || text.isBlank()) {
            throw invalidProviderOutput();
        }
        return text;
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
            JsonNode root = objectMapper.readTree(rawResponse);
            if (root == null || !root.isObject()) {
                throw invalidProviderOutput();
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw invalidProviderOutput();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private IllegalArgumentException invalidProviderOutput() {
        return new IllegalArgumentException("AI provider returned invalid structured evidence.");
    }
}
