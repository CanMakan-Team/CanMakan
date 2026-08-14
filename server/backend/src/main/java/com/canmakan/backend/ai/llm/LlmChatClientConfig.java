package com.canmakan.backend.ai.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * ChatClient beans for dietary evidence (tool-calling) and allergen-match JSON mapping.
 *
 * @author Amelia
 */
@Configuration
public class LlmChatClientConfig {

    private static final String EVIDENCE_SYSTEM_PROMPT = """
        You are CanMakan's dietary evidence agent.
        You may call dietary knowledge tools when needed.
        After tool results (or if no tools are needed), your FINAL assistant message
        MUST be only the evidence JSON object with keys resolvedIngredients and analysisNotes.
        Do not end on a tool-call-only turn. Do not emit SAFE, WARNING, or UNSAFE.
        Do not wrap the JSON in markdown fences. Do not add prose before or after the JSON.
        """;

    private static final String ALLERGEN_MATCH_SYSTEM_PROMPT = """
        You map unresolved food ingredient names to CanMakan root allergen codes.
        Use ONLY the web search text in the user message. Do not use prior knowledge.
        Respond with ONLY a JSON object:
        {"matches":[{"ingredient":"...","rootAllergen":"DAIRY"}]}
        rootAllergen must be one of DAIRY, GLUTEN, PEANUT, TREE_NUT, FISH, SHELLFISH, EGG, SOY, SESAME, MEAT, ADDITIVE, NONE.
        If the search text does not support a mapping, use NONE. Do not emit SAFE, WARNING, or UNSAFE.
        Do not wrap JSON in markdown fences. Do not call tools.
        """;

    /**
     * Chat client used by {@link LlmClient} for tool-calling evidence gathering.
     *
     * @param chatModel                         Spring AI chat model
     * @param dietaryKnowledgeToolCallbacks     the five MCP knowledge tools
     */
    @Bean
    public ChatClient dietaryEvidenceChatClient(
            ChatModel chatModel,
            @Qualifier("dietaryKnowledgeToolCallbacks") ToolCallbackProvider dietaryKnowledgeToolCallbacks
    ) {
        return ChatClient.builder(chatModel)
                .defaultSystem(EVIDENCE_SYSTEM_PROMPT)
                .defaultTools(dietaryKnowledgeToolCallbacks)
                .build();
    }

    /**
     * Chat client used to turn Tavily grounding text into structured allergen rows.
     * No dietary tools are registered, so this cannot recurse into MCP lookups.
     */
    @Bean
    @Lazy
    public ChatClient allergenMatchChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(ALLERGEN_MATCH_SYSTEM_PROMPT)
                .build();
    }
}
