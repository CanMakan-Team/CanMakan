package com.canmakan.backend.user;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.shared.exception.GlobalExceptionHandler;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.SystemRole;
import com.canmakan.backend.user.dto.NotificationPreferenceResponse;

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
 * UserPreferenceController HTTP contract tests. No family/membership setup anywhere here --
 * these routes work identically for an account with no family circle.
 *
 * @author Amelia
 */
@DisplayName("UserPreferenceController HTTP contract")
class UserPreferenceControllerTest {

    private MockMvc mockMvc;
    private UserPreferenceService userPreferenceService;

    @BeforeEach
    void setUp() {
        userPreferenceService = mock(UserPreferenceService.class);
        UserPreferenceController controller = new UserPreferenceController(userPreferenceService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setValidator(validator)
            .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/users/me/preferences/notifications returns 200")
    void getNotificationPreferenceOk() throws Exception {
        authenticateAs(10L);
        when(userPreferenceService.getNotificationPreference(10L))
            .thenReturn(new NotificationPreferenceResponse(true));

        mockMvc.perform(get("/api/users/me/preferences/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationsEnabled").value(true));
    }

    @Test
    @DisplayName("PUT /api/users/me/preferences/notifications returns 200")
    void setNotificationPreferenceOk() throws Exception {
        authenticateAs(10L);
        when(userPreferenceService.setNotificationPreference(eq(10L), eq(false)))
            .thenReturn(new NotificationPreferenceResponse(false));

        mockMvc.perform(put("/api/users/me/preferences/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notificationsEnabled\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationsEnabled").value(false));
    }

    @Test
    @DisplayName("PUT /api/users/me/preferences/notifications rejects a missing body field")
    void setNotificationPreferenceRejectsMissingField() throws Exception {
        authenticateAs(10L);

        mockMvc.perform(put("/api/users/me/preferences/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
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
