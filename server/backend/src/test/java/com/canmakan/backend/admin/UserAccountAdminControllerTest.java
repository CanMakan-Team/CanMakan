package com.canmakan.backend.admin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.admin.dto.AdminUserSummaryResponse;
import com.canmakan.backend.admin.dto.UpdateAccountStatusRequest;
import com.canmakan.backend.admin.dto.UpdateAccountStatusResponse;
import com.canmakan.backend.admin.exception.AdminUserNotFoundException;
import com.canmakan.backend.admin.exception.InvalidAccountStatusRequestException;
import com.canmakan.backend.admin.exception.ProtectedAccountOperationException;
import com.canmakan.backend.analytics.service.ConsumerTrendsService;
import com.canmakan.backend.analytics.service.UsageStatisticsService;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.SystemRole;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@DisplayName("UC13: System Admin account-management HTTP contract")
class UserAccountAdminControllerTest {

    private static final String USERS_ENDPOINT = "/api/admin/users";
    private static final String STATUS_ENDPOINT = "/api/admin/users/{userId}/status";
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 8, 10, 9, 30);

    private MockMvc mockMvc;
    private UserAccountManagementService userAccountManagementService;

    @BeforeEach
    void setUp() {
        ConsumerTrendsService consumerTrendsService = mock(ConsumerTrendsService.class);
        userAccountManagementService = mock(UserAccountManagementService.class);
        AdminScanFeedbackService adminScanFeedbackService = mock(AdminScanFeedbackService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(
                        consumerTrendsService,
                        userAccountManagementService,
                        mock(UsageStatisticsService.class),
                        adminScanFeedbackService
                ))
                .setControllerAdvice(new AdminExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /users returns the account summary contract")
    void listUsersReturnsAccountSummaries() throws Exception {
        when(userAccountManagementService.listAccounts(null, null, null))
                .thenReturn(List.of(summary(21L, SystemRole.USER, true)));

        mockMvc.perform(get(USERS_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].userId").value(21))
                .andExpect(jsonPath("$[0].email").value("user21@example.test"))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-08-10T09:30:00"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        verify(userAccountManagementService).listAccounts(null, null, null);
    }

    @Test
    @DisplayName("GET /users passes every optional filter unchanged")
    void listUsersDelegatesFiltersUnchanged() throws Exception {
        when(userAccountManagementService.listAccounts("  alice  ", "admin", false))
                .thenReturn(List.of(summary(22L, SystemRole.ADMIN, false)));

        mockMvc.perform(get(USERS_ENDPOINT)
                        .param("query", "  alice  ")
                        .param("role", "admin")
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].active").value(false));

        verify(userAccountManagementService).listAccounts("  alice  ", "admin", false);
    }

    @Test
    @DisplayName("GET /users returns an empty JSON array when no accounts match")
    void listUsersReturnsEmptyArray() throws Exception {
        when(userAccountManagementService.listAccounts(null, null, null))
                .thenReturn(List.of());

        mockMvc.perform(get(USERS_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("invalid role criteria uses the frozen 400 message response")
    void invalidRoleReturnsBadRequest() throws Exception {
        when(userAccountManagementService.listAccounts(null, "manager", null))
                .thenThrow(new InvalidAccountStatusRequestException(
                        "Role must be USER or ADMIN."
                ));

        mockMvc.perform(get(USERS_ENDPOINT).param("role", "manager"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Role must be USER or ADMIN."));
    }

    @Test
    @DisplayName("malformed active criteria is rejected before service invocation")
    void malformedActiveFilterReturnsBadRequest() throws Exception {
        mockMvc.perform(get(USERS_ENDPOINT).param("active", "not-boolean"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userAccountManagementService);
    }

    @Test
    @DisplayName("PATCH /status propagates actor and target IDs and returns changed state")
    void updateStatusPropagatesActorAndReturnsChangedResponse() throws Exception {
        authenticateAsAdmin(91L);
        UpdateAccountStatusRequest request = new UpdateAccountStatusRequest(
                false,
                "Policy violation"
        );
        when(userAccountManagementService.updateAccountStatus(91L, 31L, request))
                .thenReturn(statusResponse(31L, false, true));

        mockMvc.perform(patch(STATUS_ENDPOINT, 31L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false,"reason":"Policy violation"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(31))
                .andExpect(jsonPath("$.email").value("user31@example.test"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.changed").value(true));

        verify(userAccountManagementService).updateAccountStatus(91L, 31L, request);
    }

    @Test
    @DisplayName("PATCH /status returns 200 when the service reports no state change")
    void unchangedStatusStillReturnsOk() throws Exception {
        authenticateAsAdmin(91L);
        UpdateAccountStatusRequest request = new UpdateAccountStatusRequest(true, null);
        when(userAccountManagementService.updateAccountStatus(91L, 32L, request))
                .thenReturn(statusResponse(32L, true, false));

        mockMvc.perform(patch(STATUS_ENDPOINT, 32L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.changed").value(false));

        verify(userAccountManagementService).updateAccountStatus(91L, 32L, request);
    }

    @ParameterizedTest(name = "invalid active body {index} returns 400")
    @ValueSource(strings = {"{}", "{\"active\":null}"})
    void missingOrNullActiveReturnsBadRequest(String body) throws Exception {
        authenticateAsAdmin(91L);

        mockMvc.perform(patch(STATUS_ENDPOINT, 33L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Active status is required."));

        verifyNoInteractions(userAccountManagementService);
    }

    @Test
    @DisplayName("malformed PATCH body uses the existing 400 message response")
    void malformedBodyReturnsBadRequest() throws Exception {
        authenticateAsAdmin(91L);

        mockMvc.perform(patch(STATUS_ENDPOINT, 33L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is required."));

        verifyNoInteractions(userAccountManagementService);
    }

    @Test
    @DisplayName("missing target account maps to 404")
    void missingTargetReturnsNotFound() throws Exception {
        authenticateAsAdmin(91L);
        UpdateAccountStatusRequest request = new UpdateAccountStatusRequest(false, "Review");
        when(userAccountManagementService.updateAccountStatus(91L, 404L, request))
                .thenThrow(new AdminUserNotFoundException(404L));

        mockMvc.perform(statusPatch(404L, false, "Review"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User account not found: 404"));
    }

    @Test
    @DisplayName("invalid status request maps to 400")
    void invalidStatusRequestReturnsBadRequest() throws Exception {
        authenticateAsAdmin(91L);
        UpdateAccountStatusRequest request = new UpdateAccountStatusRequest(false, " ");
        when(userAccountManagementService.updateAccountStatus(91L, 34L, request))
                .thenThrow(new InvalidAccountStatusRequestException(
                        "Reason is required for an account status change."
                ));

        mockMvc.perform(statusPatch(34L, false, " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Reason is required for an account status change."
                ));
    }

    @Test
    @DisplayName("protected account operation maps to 409")
    void protectedOperationReturnsConflict() throws Exception {
        authenticateAsAdmin(91L);
        UpdateAccountStatusRequest request = new UpdateAccountStatusRequest(false, "Review");
        when(userAccountManagementService.updateAccountStatus(91L, 91L, request))
                .thenThrow(new ProtectedAccountOperationException(
                        "Administrators cannot change their own account status."
                ));

        mockMvc.perform(statusPatch(91L, false, "Review"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Administrators cannot change their own account status."
                ));
    }

    private static AdminUserSummaryResponse summary(
            long userId,
            SystemRole role,
            boolean active
    ) {
        return new AdminUserSummaryResponse(
                userId,
                "user" + userId + "@example.test",
                role,
                active,
                UPDATED_AT
        );
    }

    private static UpdateAccountStatusResponse statusResponse(
            long userId,
            boolean active,
            boolean changed
    ) {
        return new UpdateAccountStatusResponse(
                userId,
                "user" + userId + "@example.test",
                SystemRole.USER,
                active,
                UPDATED_AT,
                changed
        );
    }

    private static MockHttpServletRequestBuilder statusPatch(
            long userId,
            boolean active,
            String reason
    ) {
        return patch(STATUS_ENDPOINT, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":" + active + ",\"reason\":\"" + reason + "\"}");
    }

    private static void authenticateAsAdmin(long userId) {
        AuthUserDetails principal = new AuthUserDetails(
                new AuthenticatedPrincipal(
                        userId,
                        "admin" + userId + "@example.test",
                        true,
                        SystemRole.ADMIN
                ),
                "{noop}unused"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
    }
}
