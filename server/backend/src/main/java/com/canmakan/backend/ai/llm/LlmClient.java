package com.canmakan.backend.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
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
@Slf4j
@Service
public class LlmClient {

    /** Cap logged raw body length so a huge model reply does not flood the console. */
    private static final int RAW_LOG_MAX_CHARS = 4000;

    /**
     * One extra turn when the tool loop ends with blank text — asks for the
     * evidence JSON without dropping tool registration on the ChatClient.
     */
    private static final String BLANK_CONTENT_RETRY_SUFFIX = """

        FINAL_OUTPUT_REQUIRED:
        Your previous assistant turn had no text body (tool calls only or empty).
        Emit exactly one final assistant message that is ONLY the evidence JSON object
        from OUTPUT_SCHEMA (resolvedIngredients + analysisNotes). No markdown, no prose.
        """;

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
        ProviderTurn turn = callProvider(compiledPrompt);

        if (isBlank(turn.rawContent())) {
            logBlankDiagnostics(turn.chatResponse());
            log.warn("AI evidence content blank after tools; retrying once for final JSON only.");
            turn = callProvider(compiledPrompt + BLANK_CONTENT_RETRY_SUFFIX);
        }

        if (isBlank(turn.rawContent())) {
            logBlankDiagnostics(turn.chatResponse());
            log.warn("AI evidence response was blank or null after retry; cannot parse structured evidence.");
            throw invalidProviderOutput();
        }

        EvidencePayload evidence;
        String rawResponse = turn.rawContent();
        try {
            evidence = resolveEvidence(turn);
            rawResponse = serializeEvidenceOrRaw(evidence, turn.rawContent());
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "AI evidence JSON parse failed ({} chars). Raw response (truncated): {}",
                    turn.rawContent().length(),
                    truncateForLog(turn.rawContent())
            );
            throw ex;
        }

        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
        List<ResolvedIngredient> resolvedIngredients = toResolvedIngredients(evidence);
        String analysisNotes = evidence.analysisNotes() == null ? "" : evidence.analysisNotes();

        ChatResponseMetadata metadata =
                turn.chatResponse() == null ? null : turn.chatResponse().getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();

        log.debug(
                "AI evidence parsed: {} resolved ingredient(s), latencyMs={}",
                resolvedIngredients.size(),
                latencyMs
        );

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

    private ProviderTurn callProvider(String userPrompt) {
        try {
            var call = chatClient.prompt()
                    .user(userPrompt)
                    .call();
            ChatResponse response = call.chatResponse();
            String content = firstNonBlank(call.content(), textFromChatResponse(response));
            return new ProviderTurn(call, response, content);
        } catch (RuntimeException exception) {
            log.warn(
                    "AI provider request failed ({})",
                    exception.getClass().getSimpleName()
            );
            // Do not chain the cause: provider errors can embed secrets in the message.
            throw new IllegalStateException("AI provider request failed.");
        }
    }

    /**
     * Prefer Spring AI structured entity conversion; fall back to manual JSON parse.
     */
    private EvidencePayload resolveEvidence(ProviderTurn turn) {
        try {
            EvidencePayload fromEntity = turn.call().entity(EvidencePayload.class);
            if (fromEntity != null && fromEntity.resolvedIngredients() != null) {
                return fromEntity;
            }
        } catch (RuntimeException ex) {
            log.debug(
                    "ChatClient.entity(EvidencePayload) failed ({}); falling back to JSON parse",
                    ex.getClass().getSimpleName()
            );
        }
        return parseEvidencePayload(turn.rawContent());
    }

    private EvidencePayload parseEvidencePayload(String rawResponse) {
        JsonNode root = parseRoot(rawResponse);
        JsonNode ingredientsNode = root.get("resolvedIngredients");
        if (ingredientsNode == null || !ingredientsNode.isArray()) {
            throw invalidProviderOutput();
        }

        List<EvidencePayload.ResolvedIngredientEvidence> items = new ArrayList<>();
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

            items.add(new EvidencePayload.ResolvedIngredientEvidence(
                    nameNode.textValue(),
                    rootAllergen,
                    confidenceNode.doubleValue()
            ));
        }

        JsonNode notesNode = root.get("analysisNotes");
        String notes = "";
        if (notesNode != null && !notesNode.isNull()) {
            if (!notesNode.isTextual()) {
                throw invalidProviderOutput();
            }
            notes = notesNode.textValue();
        }

        return new EvidencePayload(List.copyOf(items), notes);
    }

    private List<ResolvedIngredient> toResolvedIngredients(EvidencePayload evidence) {
        List<ResolvedIngredient> resolved = new ArrayList<>();
        for (EvidencePayload.ResolvedIngredientEvidence item : evidence.resolvedIngredients()) {
            if (item == null) {
                throw invalidProviderOutput();
            }
            try {
                Double confidence = item.confidence();
                if (confidence == null) {
                    throw invalidProviderOutput();
                }
                resolved.add(new ResolvedIngredient(
                        item.ingredientName(),
                        item.rootAllergen(),
                        confidence
                ));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw invalidProviderOutput();
            }
        }
        return List.copyOf(resolved);
    }

    private JsonNode parseRoot(String rawResponse) {
        try {
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

    private void logBlankDiagnostics(ChatResponse response) {
        if (response == null) {
            log.warn("AI blank evidence diagnostics: chatResponse=null");
            return;
        }
        Generation generation = response.getResult();
        AssistantMessage output = generation == null ? null : generation.getOutput();
        String text = output == null ? null : output.getText();
        boolean hasTools = response.hasToolCalls() || (output != null && output.hasToolCalls());
        int toolCallCount = 0;
        String toolNames = "";
        if (output != null && output.getToolCalls() != null) {
            toolCallCount = output.getToolCalls().size();
            toolNames = output.getToolCalls().stream()
                    .map(AssistantMessage.ToolCall::name)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(","));
        }
        Object finishReason = generation == null || generation.getMetadata() == null
                ? null
                : generation.getMetadata().getFinishReason();
        log.warn(
                "AI blank evidence diagnostics: hasToolCalls={}, toolCallCount={}, toolNames=[{}], "
                        + "textLength={}, finishReason={}, results={}",
                hasTools,
                toolCallCount,
                toolNames,
                text == null ? -1 : text.length(),
                finishReason,
                response.getResults() == null ? 0 : response.getResults().size()
        );
    }

    private static String textFromChatResponse(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private String serializeEvidenceOrRaw(EvidencePayload evidence, String rawContent) {
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException exception) {
            return rawContent;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String primary, String secondary) {
        if (!isBlank(primary)) {
            return primary;
        }
        if (!isBlank(secondary)) {
            return secondary;
        }
        return primary;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncateForLog(String rawResponse) {
        if (rawResponse.length() <= RAW_LOG_MAX_CHARS) {
            return rawResponse;
        }
        return rawResponse.substring(0, RAW_LOG_MAX_CHARS) + "...[truncated]";
    }

    private IllegalArgumentException invalidProviderOutput() {
        return new IllegalArgumentException("AI provider returned invalid structured evidence.");
    }

    private record ProviderTurn(
            ChatClient.CallResponseSpec call,
            ChatResponse chatResponse,
            String rawContent
    ) {
    }
}
