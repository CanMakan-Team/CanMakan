package com.canmakan.backend.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.SystemRole;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit test for the UC15 heartbeat endpoint: it records a heartbeat for the authenticated user and
 * returns 204, taking the user id from the principal rather than the request body.
 *
 * @author XieHuayuan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UC15: SessionController heartbeat endpoint")
class SessionControllerTest {

    @Mock
    private SessionTrackingService sessionTrackingService;

    @InjectMocks
    private SessionController controller;

    @Test
    @DisplayName("records a heartbeat for the authenticated user and returns 204")
    void heartbeatRecordsForAuthenticatedUser() {
        AuthUserDetails principal = new AuthUserDetails(
                new AuthenticatedPrincipal(7L, "user@example.com", true, SystemRole.USER),
                "hashed-password");

        ResponseEntity<Void> response = controller.heartbeat(principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sessionTrackingService).recordHeartbeat(7L);
    }
}
