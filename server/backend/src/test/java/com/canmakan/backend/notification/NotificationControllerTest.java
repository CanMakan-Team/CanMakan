package com.canmakan.backend.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.SystemRole;
import java.time.Instant;
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

@DisplayName("NotificationController HTTP contract")
class NotificationControllerTest {

    private MockMvc mockMvc;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        NotificationController controller = new NotificationController(notificationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/notifications/me returns inbox rows")
    void listOk() throws Exception {
        authenticateAs(10L);
        when(notificationService.listMine(10L)).thenReturn(List.of(
            new UserNotificationResponse(
                1L,
                NotificationType.FAMILY_INVITE_UPDATE,
                "Invite sent to jamie@example.com.",
                "Wong Family",
                null,
                false,
                false,
                Instant.parse("2026-08-14T00:00:00Z")
            )
        ));

        mockMvc.perform(get("/api/notifications/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Invite sent to jamie@example.com."))
            .andExpect(jsonPath("$[0].type").value("FAMILY_INVITE_UPDATE"))
            .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    @DisplayName("POST /api/notifications/me/read returns 204")
    void markReadOk() throws Exception {
        authenticateAs(10L);

        mockMvc.perform(post("/api/notifications/me/read"))
            .andExpect(status().isNoContent());

        verify(notificationService).markAllRead(10L);
    }

    @Test
    @DisplayName("DELETE /api/notifications/{id} returns 204")
    void deleteOk() throws Exception {
        authenticateAs(10L);

        mockMvc.perform(delete("/api/notifications/9"))
            .andExpect(status().isNoContent());

        verify(notificationService).deleteMine(10L, 9L);
    }

    @Test
    @DisplayName("DELETE /api/notifications/{id} returns 404 when missing")
    void deleteMissing() throws Exception {
        authenticateAs(10L);
        org.mockito.Mockito.doThrow(new NotificationNotFoundException("Notification was not found."))
            .when(notificationService)
            .deleteMine(10L, 9L);

        mockMvc.perform(delete("/api/notifications/9"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Notification was not found."));
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
