package com.canmakan.backend.product.recommendation.discovery;

import com.canmakan.backend.product.recommendation.catalog.CatalogProduct;
import com.canmakan.backend.product.recommendation.catalog.CatalogProductRepository;
import com.canmakan.backend.product.recommendation.dto.RecommendationRequest;
import com.canmakan.backend.product.recommendation.history.RecommendationLogService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("UC5 Tier B: LlmRecommendationDiscoveryService")
class LlmRecommendationDiscoveryServiceTest {

    @Mock
    private CatalogProductRepository catalogProductRepository;

    @Mock
    private RecommendationLogService recommendationLogService;

    private ChatClient chatClient;
    private ChatClient.CallResponseSpec callResponseSpec;
    private LlmRecommendationDiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        Mockito.lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        Mockito.lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        Mockito.lenient().when(requestSpec.call()).thenReturn(callResponseSpec);

        discoveryService = new LlmRecommendationDiscoveryService(
                chatClient,
                catalogProductRepository,
                recommendationLogService,
                new ObjectMapper(),
                true,
                "gpt-4o-mini");
    }

    @Test
    void resolvesOnlyExistingCatalogBarcodes() {
        CatalogProduct source = product("8888200602857", "Farmhouse Fresh Milk");
        CatalogProduct substitute = product("8850025000521", "Soya Milk Unsweetened");

        ChatResponse response = chatResponse("""
                {"candidates":[
                  {"barcode":"8850025000521","productName":"Soya Milk Unsweetened","brand":"Home Soy","reason":"dairy free"},
                  {"barcode":"missing-barcode","productName":"Unknown","brand":"","reason":"hallucinated"}
                ]}
                """);
        when(callResponseSpec.chatResponse()).thenReturn(response);
        when(catalogProductRepository.findById("8850025000521")).thenReturn(Optional.of(substitute));

        List<CatalogProduct> candidates = discoveryService.discoverCandidates(
                new RecommendationRequest(3L, "8888200602857", 10L),
                source,
                List.of());

        assertEquals(1, candidates.size());
        assertEquals("8850025000521", candidates.getFirst().getBarcode());
        verify(recommendationLogService).recordDiscoveryAudit(any());
    }

    @Test
    void disabledServiceReturnsEmptyWithoutProviderCall() {
        LlmRecommendationDiscoveryService disabled = new LlmRecommendationDiscoveryService(
                chatClient,
                catalogProductRepository,
                recommendationLogService,
                new ObjectMapper(),
                false,
                "gpt-4o-mini");

        List<CatalogProduct> candidates = disabled.discoverCandidates(
                new RecommendationRequest(3L, "8888200602857", 10L),
                product("8888200602857", "Farmhouse Fresh Milk"),
                List.of());

        assertTrue(candidates.isEmpty());
        verify(chatClient, never()).prompt();
    }

    private static CatalogProduct product(String barcode, String name) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName(name);
        product.setMainCategoryEn("Fresh milks");
        product.setIngredientsText("Fresh milks");
        return product;
    }

    private static ChatResponse chatResponse(String json) {
        AssistantMessage message = new AssistantMessage(json);
        Generation generation = new Generation(message);
        Usage usage = org.mockito.Mockito.mock(Usage.class);
        org.mockito.Mockito.when(usage.getPromptTokens()).thenReturn(100);
        org.mockito.Mockito.when(usage.getCompletionTokens()).thenReturn(20);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(usage).build();
        return new ChatResponse(List.of(generation), metadata);
    }
}
