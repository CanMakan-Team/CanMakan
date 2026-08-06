package com.canmakan.backend.ai.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Tier-3 evidence {@link ChatClient} with all dietary knowledge tools
 * registered so the model can call them autonomously during assessment.
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
}
