package com.canmakan.backend.product.recommendation;

import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("UC17: RecommendationHistoryController HTTP contract")
class RecommendationHistoryControllerTest {

    private MockMvc mockMvc;
    private FamilyAuthorizationService familyAuthorization;
    private RecommendationHistoryService recommendationHistoryService;

    @BeforeEach
    void setUp() {
        familyAuthorization = org.mockito.Mockito.mock(FamilyAuthorizationService.class);
        recommendationHistoryService = org.mockito.Mockito.mock(RecommendationHistoryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RecommendationHistoryController(familyAuthorization, recommendationHistoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getRecommendationHistoryReturnsGroupedResponse() throws Exception {
        authenticateAs(7L);
        when(recommendationHistoryService.getHistoryForProfile(1L)).thenReturn(
                new RecommendationHistoryResponse(
                        1L,
                        List.of(new RecommendationHistoryEntryDto(
                                2L,
                                "0038527591039",
                                "Oatmeal Squares Original",
                                "Quaker",
                                "UNSAFE",
                                "2026-08-03T10:04:30",
                                List.of(new RecommendationHistoryAlternativeDto(
                                        "9315090200706",
                                        "Ancient grain flakes",
                                        "Freedom Foods",
                                        "category_match",
                                        new BigDecimal("1.0000"),
                                        "TIER_A_CATALOG"
                                ))
                        ))
                ));

        mockMvc.perform(get("/api/profiles/1/recommendation-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(1))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].scanId").value(2))
                .andExpect(jsonPath("$.history[0].sourceBarcode").value("0038527591039"))
                .andExpect(jsonPath("$.history[0].sourceVerdict").value("UNSAFE"))
                .andExpect(jsonPath("$.history[0].alternatives[0].barcode").value("9315090200706"));

        verify(familyAuthorization).assertProfileAuthorizedForScan(7L, 1L);
        verify(recommendationHistoryService).getHistoryForProfile(1L);
    }

    @Test
    void getRecommendationHistoryReturnsEmptyList() throws Exception {
        authenticateAs(7L);
        when(recommendationHistoryService.getHistoryForProfile(1L))
                .thenReturn(RecommendationHistoryResponse.empty(1L));

        mockMvc.perform(get("/api/profiles/1/recommendation-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(1))
                .andExpect(jsonPath("$.history.length()").value(0));
    }

    @Test
    void getRecommendationHistoryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/profiles/1/recommendation-history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authenticated user was not found."));

        verify(recommendationHistoryService, never()).getHistoryForProfile(any());
    }

    @Test
    void getRecommendationHistoryReturns403ForUnauthorizedProfile() throws Exception {
        authenticateAs(7L);
        doThrow(new FamilyForbiddenException("Profile does not belong to your family circle."))
                .when(familyAuthorization)
                .assertProfileAuthorizedForScan(7L, 55L);

        mockMvc.perform(get("/api/profiles/55/recommendation-history"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Profile does not belong to your family circle."));

        verify(recommendationHistoryService, never()).getHistoryForProfile(any());
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
