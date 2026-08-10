package com.canmakan.backend.product.scan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.integration.BarcodeValidationClient;
import com.canmakan.backend.product.assessment.AssessmentOrchestrator;
import com.canmakan.backend.product.assessment.AssessmentRequest;
import com.canmakan.backend.product.assessment.AssessmentResponse;
import com.canmakan.backend.product.assessment.ExecutionTier;
import com.canmakan.backend.shared.exception.GlobalExceptionHandler;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.SystemRole;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP contract tests for the two-step scan APIs on {@link ScanController}.
 *
 * @author Amelia
 */
@DisplayName("UC2: ScanController validate + assess HTTP contract")
class ScanControllerTest {

    private MockMvc mockMvc;
    private BarcodeValidationClient validationClient;
    private AssessmentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        validationClient = mock(BarcodeValidationClient.class);
        orchestrator = mock(AssessmentOrchestrator.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ScanController(validationClient, orchestrator))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("UC2 BE1: POST /api/scan/validate returns the validation client response")
    void validateReturnsClientResponse() throws Exception {
        when(validationClient.validateProduct("3017620422003"))
            .thenReturn(new ValidationResponse(true, "food", "Valid food product"));

        mockMvc.perform(post("/api/scan/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"3017620422003\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.validFood").value(true))
            .andExpect(jsonPath("$.category").value("food"))
            .andExpect(jsonPath("$.message").value("Valid food product"));

        verify(validationClient).validateProduct("3017620422003");
    }

    @Test
    @DisplayName("UC2 BE2: POST /api/scan/assess uses JWT principal user id")
    void assessUsesAuthenticatedUserId() throws Exception {
        authenticateAs(7L);
        when(orchestrator.assess(eq(7L), any(AssessmentRequest.class)))
            .thenReturn(sampleAssessment());

        mockMvc.perform(post("/api/scan/assess")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"3017620422003\",\"profileId\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verdict").value("SAFE"))
            .andExpect(jsonPath("$.scanId").value(42))
            .andExpect(jsonPath("$.productName").value("Nutella"))
            .andExpect(jsonPath("$.barcode").value("3017620422003"));

        verify(orchestrator).assess(eq(7L), eq(new AssessmentRequest("3017620422003", 1L)));
    }

    @Test
    @DisplayName("UC2 BE3: POST /api/scan/assess without principal returns 401")
    void assessRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/scan/assess")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"3017620422003\",\"profileId\":1}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authenticated user was not found."));

        verify(orchestrator, never()).assess(any(), any());
    }

    @Test
    @DisplayName("UC2 BE4: POST /api/scan/assess returns 400 when profileId is missing")
    void assessRequiresProfileId() throws Exception {
        authenticateAs(7L);
        mockMvc.perform(post("/api/scan/assess")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"3017620422003\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Profile ID is required"));

        verify(orchestrator, never()).assess(any(), any());
    }

    @Test
    @DisplayName("UC2 BE5: POST /api/scan/assess returns 403 for unauthorized profile")
    void assessRejectsUnauthorizedProfile() throws Exception {
        authenticateAs(7L);
        when(orchestrator.assess(eq(7L), any(AssessmentRequest.class)))
            .thenThrow(new FamilyForbiddenException(
                "Profile does not belong to your family circle."));

        mockMvc.perform(post("/api/scan/assess")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"3017620422003\",\"profileId\":55}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message")
                .value("Profile does not belong to your family circle."));
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

    private static AssessmentResponse sampleAssessment() {
        return new AssessmentResponse(
            "SAFE",
            "ok",
            List.of(),
            ExecutionTier.TIER_1_RULES,
            42L,
            "Nutella",
            "3017620422003"
        );
    }
}
