package com.canmakan.backend.knowledgebase.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.knowledgebase.mcp.contract.ExternalAllergenMatchPayload;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

@DisplayName("UC3: ExternalAllergenMatchMapper")
class ExternalAllergenMatchMapperTest {

    @Test
    @DisplayName("Blank search text does not call ChatClient")
    void blankSearchTextSkipsChatClient() {
        ChatClient chatClient = mock(ChatClient.class);
        ExternalAllergenMatchMapper mapper = new ExternalAllergenMatchMapper(chatClient, true);

        assertThat(mapper.map(List.of("Casein"), "  ")).isEmpty();
        verify(chatClient, never()).prompt();
    }

    @Test
    @DisplayName("AI disabled uses regex parser on Tavily text")
    void aiDisabledUsesParser() {
        ChatClient chatClient = mock(ChatClient.class);
        ExternalAllergenMatchMapper mapper = new ExternalAllergenMatchMapper(chatClient, false);

        List<Ingredient> matches = mapper.map(List.of("Casein"), "Casein -> DAIRY");

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().rootAllergen()).isEqualTo("DAIRY");
        verify(chatClient, never()).prompt();
    }

    @Test
    @DisplayName("AI enabled maps ChatClient JSON onto unresolved names")
    void aiEnabledMapsChatClientJson() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        Mockito.lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        Mockito.lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        Mockito.lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(ExternalAllergenMatchPayload.class)).thenReturn(
            new ExternalAllergenMatchPayload(List.of(
                new ExternalAllergenMatchPayload.Match("Casein", "DAIRY"),
                new ExternalAllergenMatchPayload.Match("invented-name", "PEANUT"),
                new ExternalAllergenMatchPayload.Match("Inulin", "NONE")
            )));

        ExternalAllergenMatchMapper mapper = new ExternalAllergenMatchMapper(chatClient, true);
        List<Ingredient> matches = mapper.map(
            List.of("Casein", "Inulin"),
            "Answer: casein is a milk protein; inulin is a fibre.");

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).ingredientName()).isEqualTo("Casein");
        assertThat(matches.get(0).rootAllergen()).isEqualTo("DAIRY");
        assertThat(matches.get(1).ingredientName()).isEqualTo("Inulin");
        assertThat(matches.get(1).rootAllergen()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("ChatClient failure falls back to regex parser")
    void chatClientFailureFallsBackToParser() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new RuntimeException("provider down"));
        ExternalAllergenMatchMapper mapper = new ExternalAllergenMatchMapper(chatClient, true);

        List<Ingredient> matches = mapper.map(List.of("Casein"), "Casein -> DAIRY");

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().rootAllergen()).isEqualTo("DAIRY");
    }
}
