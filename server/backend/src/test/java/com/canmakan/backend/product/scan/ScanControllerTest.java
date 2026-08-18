package com.canmakan.backend.product.scan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.family.service.FamilyAuthorizationService;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.integration.BarcodeValidationClient;
import com.canmakan.backend.product.assessment.AssessmentOrchestrator;
import com.canmakan.backend.product.assessment.ExecutionTier;
import com.canmakan.backend.product.assessment.dto.AssessmentRequest;
import com.canmakan.backend.product.assessment.dto.AssessmentResponse;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

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
    private ScanHistoryService scanHistoryService;
    private FamilyAuthorizationService familyAuthorization;
    private ScanFeedbackService scanFeedbackService;

    @BeforeEach
    void setUp() {
        validationClient = mock(BarcodeValidationClient.class);
        orchestrator = mock(AssessmentOrchestrator.class);
        scanHistoryService = mock(ScanHistoryService.class);
        familyAuthorization = mock(FamilyAuthorizationService.class);
        scanFeedbackService = mock(ScanFeedbackService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ScanController(
                    validationClient, orchestrator, scanHistoryService, familyAuthorization,
                    scanFeedbackService))
            .setControllerAdvice(new GlobalExceptionHandler(), new ScanExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setValidator(validator)
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

    @Test
    @DisplayName("UC4: GET /api/scan/history/{id} returns history rows for authorized profile")
    void historyReturnsRows() throws Exception {
        authenticateAs(7L);
        when(scanHistoryService.getScanHistoryForProfile(1L)).thenReturn(List.of(
            new ScanHistoryResponse(
                10L,
                1L,
                "3017620422003",
                new ScanHistoryResponse.ProductDto("Nutella", "Ferrero", "3017620422003"),
                "2026-07-28T18:42:00",
                "SAFE",
                new ScanHistoryResponse.FindingsDto(List.of(), List.of()),
                "ok")
        ));

        mockMvc.perform(get("/api/scan/history/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(10))
            .andExpect(jsonPath("$[0].verdict").value("SAFE"));

        verify(familyAuthorization).assertProfileAuthorizedForScan(7L, 1L);
        verify(scanHistoryService).getScanHistoryForProfile(1L);
    }

    @Test
    @DisplayName("UC4: GET /api/scan/history/{id} without principal returns 401")
    void historyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/scan/history/1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authenticated user was not found."));

        verify(scanHistoryService, never()).getScanHistoryForProfile(any());
    }

    @Test
    @DisplayName("UC4: GET /api/scan/history/{id} returns 403 for unauthorized profile")
    void historyRejectsUnauthorizedProfile() throws Exception {
        authenticateAs(7L);
        org.mockito.Mockito.doThrow(new FamilyForbiddenException(
                "Profile does not belong to your family circle."))
            .when(familyAuthorization)
            .assertProfileAuthorizedForScan(7L, 55L);

        mockMvc.perform(get("/api/scan/history/55"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message")
                .value("Profile does not belong to your family circle."));

        verify(scanHistoryService, never()).getScanHistoryForProfile(any());
    }

    @Test
    @DisplayName("UC20 BE1: POST /api/scan/{id}/feedback saves a thumbs down with a comment")
    void feedbackSavesCommentedReport() throws Exception {
        authenticateAs(7L);
        when(scanFeedbackService.submitFeedback(7L, 19L, false, "Wrong allergen listed"))
            .thenReturn(new ScanFeedbackResponse(3L, 19L, false, "Wrong allergen listed", false,
                "2026-08-16T15:04:00"));

        mockMvc.perform(post("/api/scan/19/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isPositive\":false,\"userComments\":\"Wrong allergen listed\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.scanId").value(19))
            .andExpect(jsonPath("$.isPositive").value(false))
            .andExpect(jsonPath("$.userComments").value("Wrong allergen listed"))
            .andExpect(jsonPath("$.resolved").value(false));

        verify(scanFeedbackService).submitFeedback(7L, 19L, false, "Wrong allergen listed");
    }

    @Test
    @DisplayName("UC20 BE2: POST /api/scan/{id}/feedback saves a bare thumbs down (no comment)")
    void feedbackSavesReportWithoutComment() throws Exception {
        authenticateAs(7L);
        when(scanFeedbackService.submitFeedback(7L, 19L, false, null))
            .thenReturn(new ScanFeedbackResponse(4L, 19L, false, null, false, "2026-08-16T15:05:00"));

        mockMvc.perform(post("/api/scan/19/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isPositive\":false}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userComments").doesNotExist());

        verify(scanFeedbackService).submitFeedback(7L, 19L, false, null);
    }

    @Test
    @DisplayName("UC20 BE3: POST /api/scan/{id}/feedback saves a thumbs up")
    void feedbackSavesPositiveReport() throws Exception {
        authenticateAs(7L);
        when(scanFeedbackService.submitFeedback(7L, 19L, true, null))
            .thenReturn(new ScanFeedbackResponse(5L, 19L, true, null, false, "2026-08-16T15:06:00"));

        mockMvc.perform(post("/api/scan/19/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isPositive\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isPositive").value(true))
            .andExpect(jsonPath("$.userComments").doesNotExist());

        verify(scanFeedbackService).submitFeedback(7L, 19L, true, null);
    }

    @Test
    @DisplayName("UC20 BE4: POST /api/scan/{id}/feedback returns 400 when isPositive is missing")
    void feedbackRequiresIsPositive() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/api/scan/19/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("isPositive is required"));

        verify(scanFeedbackService, never()).submitFeedback(anyLong(), anyLong(), anyBoolean(), any());
    }

    @Test
    @DisplayName("UC20 BE5: POST /api/scan/{id}/feedback without principal returns 401")
    void feedbackRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/scan/19/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isPositive\":false}"))
            .andExpect(status().isUnauthorized());

        verify(scanFeedbackService, never()).submitFeedback(anyLong(), anyLong(), anyBoolean(), any());
    }

    @Test
    @DisplayName("UC20 BE6: POST /api/scan/{id}/feedback returns 404 for an unknown scan")
    void feedbackRejectsUnknownScan() throws Exception {
        authenticateAs(7L);
        when(scanFeedbackService.submitFeedback(7L, 999L, false, null))
            .thenThrow(new ScanNotFoundException("Scan was not found."));

        mockMvc.perform(post("/api/scan/999/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isPositive\":false}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Scan was not found."));
    }

    @Test
    @DisplayName("UC20 BE7: POST /api/scan/{id}/feedback returns 403 for a scan outside the caller's family")
    void feedbackRejectsUnauthorizedScan() throws Exception {
        authenticateAs(7L);
        when(scanFeedbackService.submitFeedback(7L, 19L, false, null))
            .thenThrow(new FamilyForbiddenException("Profile does not belong to your family circle."));

        mockMvc.perform(post("/api/scan/19/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isPositive\":false}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Profile does not belong to your family circle."));
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
