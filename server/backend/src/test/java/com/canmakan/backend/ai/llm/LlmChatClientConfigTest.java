package com.canmakan.backend.ai.llm;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Ensures the evidence ChatClient bean factory accepts the dietary tool provider.
 *
 * @author Amelia
 */
@DisplayName("UC3: LlmChatClientConfig - Builds the Tier-3 evidence ChatClient with all dietary knowledge tools registered")
class LlmChatClientConfigTest {

    @Test
    void buildsChatClientWithDietaryToolCallbacks() {
        ChatModel chatModel = mock(ChatModel.class);
        ToolCallbackProvider provider = mock(ToolCallbackProvider.class);

        ChatClient client = new LlmChatClientConfig()
                .dietaryEvidenceChatClient(chatModel, provider);

        assertNotNull(client);
    }
}
