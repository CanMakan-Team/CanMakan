package com.canmakan.backend.product.recommendation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient for UC5 Tier-B substitute discovery (JSON candidates only; no verdict).
 */
@Configuration
public class RecommendationLlmConfig {

    private static final String DISCOVERY_SYSTEM_PROMPT = """
        You are CanMakan's product substitute discovery agent.
        Given a scanned product and dietary profile restrictions, suggest safer substitute products
        that likely exist in a Singapore grocery catalog.
        Respond with ONLY a JSON object:
        {"candidates":[{"barcode":"...","productName":"...","brand":"...","reason":"..."}]}
        Rules:
        - Suggest up to 5 substitutes.
        - Prefer plant-based or allergen-appropriate alternatives when restrictions require it.
        - Do NOT emit SAFE, WARNING, or UNSAFE.
        - Do NOT wrap JSON in markdown fences.
        - If unsure of barcode, omit that candidate rather than inventing one.
        """;

    @Bean
    public ChatClient recommendationDiscoveryChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(DISCOVERY_SYSTEM_PROMPT)
                .build();
    }
}
