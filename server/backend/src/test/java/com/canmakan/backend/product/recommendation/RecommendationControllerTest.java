package com.canmakan.backend.product.recommendation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC5: RecommendationController HTTP contract")
class RecommendationControllerTest {

    @Mock
    private RecommendationService recommendationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RecommendationController(recommendationService))
                .build();
    }

    @Test
    void getRecommendationsReturnsServiceResponse() throws Exception {
        when(recommendationService.recommend(eq(new RecommendationRequest(1L, "0038527591039", 2L))))
                .thenReturn(new AlternativeProductResponse(
                        "0038527591039",
                        List.of(new AlternativeProductDto(
                                "9315090200706",
                                "Ancient grain flakes",
                                "Freedom Foods",
                                "category_match",
                                new BigDecimal("0.99")
                        ))
                ));

        mockMvc.perform(get("/api/profiles/1/recommendations")
                        .param("sourceBarcode", "0038527591039")
                        .param("scanId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceBarcode").value("0038527591039"))
                .andExpect(jsonPath("$.alternatives.length()").value(1))
                .andExpect(jsonPath("$.alternatives[0].barcode").value("9315090200706"))
                .andExpect(jsonPath("$.alternatives[0].productName").value("Ancient grain flakes"))
                .andExpect(jsonPath("$.alternatives[0].matchReason").value("category_match"));

        verify(recommendationService).recommend(new RecommendationRequest(1L, "0038527591039", 2L));
    }

    @Test
    void getRecommendationsAllowsMissingScanId() throws Exception {
        when(recommendationService.recommend(eq(new RecommendationRequest(1L, "0038527591039", null))))
                .thenReturn(AlternativeProductResponse.empty("0038527591039"));

        mockMvc.perform(get("/api/profiles/1/recommendations")
                        .param("sourceBarcode", "0038527591039"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceBarcode").value("0038527591039"))
                .andExpect(jsonPath("$.alternatives.length()").value(0));

        verify(recommendationService).recommend(new RecommendationRequest(1L, "0038527591039", null));
    }
}
