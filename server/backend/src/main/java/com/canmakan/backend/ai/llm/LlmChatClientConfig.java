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
            .defaultTools(dietaryKnowledgeToolCallbacks)
            .build();
    }
}
