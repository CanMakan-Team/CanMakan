package com.canmakan.backend.product.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.family.FamilyAuthorizationService;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.shared.exception.GlobalExceptionHandler;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.SystemRole;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC5: RecommendationController HTTP contract")
class RecommendationControllerTest {

    @Mock
    private FamilyAuthorizationService familyAuthorization;

    @Mock
    private RecommendationService recommendationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RecommendationController(familyAuthorization, recommendationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getRecommendationsReturnsServiceResponse() throws Exception {
        authenticateAs(7L);
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

        verify(familyAuthorization).assertProfileAuthorizedForScan(7L, 1L);
        verify(recommendationService).recommend(new RecommendationRequest(1L, "0038527591039", 2L));
    }

    @Test
    void getRecommendationsAllowsMissingScanId() throws Exception {
        authenticateAs(7L);
        when(recommendationService.recommend(eq(new RecommendationRequest(1L, "0038527591039", null))))
                .thenReturn(AlternativeProductResponse.empty("0038527591039"));

        mockMvc.perform(get("/api/profiles/1/recommendations")
                        .param("sourceBarcode", "0038527591039"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceBarcode").value("0038527591039"))
                .andExpect(jsonPath("$.alternatives.length()").value(0));

        verify(familyAuthorization).assertProfileAuthorizedForScan(7L, 1L);
        verify(recommendationService).recommend(new RecommendationRequest(1L, "0038527591039", null));
    }

    @Test
    void getRecommendationsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/profiles/1/recommendations")
                        .param("sourceBarcode", "0038527591039"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authenticated user was not found."));

        verify(recommendationService, never()).recommend(any());
    }

    @Test
    void getRecommendationsReturns403ForUnauthorizedProfile() throws Exception {
        authenticateAs(7L);
        doThrow(new FamilyForbiddenException("Profile does not belong to your family circle."))
                .when(familyAuthorization)
                .assertProfileAuthorizedForScan(7L, 55L);

        mockMvc.perform(get("/api/profiles/55/recommendations")
                        .param("sourceBarcode", "0038527591039"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Profile does not belong to your family circle."));

        verify(recommendationService, never()).recommend(any());
    }

    private static void authenticateAs(long userId) {
        AuthUserDetails principal = new AuthUserDetails(
                new AuthenticatedPrincipal(userId, "user" + userId + "@example.com", true, SystemRole.USER),
                "{noop}unused"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
