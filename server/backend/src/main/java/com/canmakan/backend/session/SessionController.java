package com.canmakan.backend.session;

import com.canmakan.backend.shared.security.AuthUserChecker;
import com.canmakan.backend.shared.security.AuthUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives in-app session heartbeats from the mobile client (UC15 engagement tracking).
 *
 * <p>{@code POST /api/sessions/heartbeat} is authenticated (covered by the {@code /api/**} rule); the
 * user is taken from the authenticated principal, never from the request body.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionTrackingService sessionTrackingService;

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal AuthUserDetails principal) {
        long userId = AuthUserChecker.requireUserId(principal);
        sessionTrackingService.recordHeartbeat(userId);
        return ResponseEntity.noContent().build();
    }
}
