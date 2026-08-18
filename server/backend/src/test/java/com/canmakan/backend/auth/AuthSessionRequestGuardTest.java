package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.canmakan.backend.auth.exception.AuthSessionRequestRejectedException;
import com.canmakan.backend.auth.config.RefreshTokenProperties;
import com.canmakan.backend.shared.security.CorsProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class AuthSessionRequestGuardTest {

    @Test
    void acceptsExactConfiguredBrowserOriginWithIntentHeader() {
        AuthSessionRequestGuard guard = guard("Lax", false, List.of("https://app.example.test"));
        HttpServletRequest request = request("1", "https://app.example.test");

        assertDoesNotThrow(() -> guard.requireTrustedSessionMutation(request));
    }

    @Test
    void acceptsNativeStyleRequestWithoutOriginButWithIntentHeader() {
        AuthSessionRequestGuard guard = guard("Lax", false, List.of("https://app.example.test"));

        assertDoesNotThrow(() ->
            guard.requireTrustedSessionMutation(request("1", null))
        );
    }

    @Test
    void rejectsMissingHeaderUnauthorizedOriginAndNullOrigin() {
        AuthSessionRequestGuard guard = guard("Lax", false, List.of("https://app.example.test"));

        assertThrows(AuthSessionRequestRejectedException.class, () ->
            guard.requireTrustedSessionMutation(request(null, "https://app.example.test"))
        );
        assertThrows(AuthSessionRequestRejectedException.class, () ->
            guard.requireTrustedSessionMutation(request("1", "https://evil.example"))
        );
        assertThrows(AuthSessionRequestRejectedException.class, () ->
            guard.requireTrustedSessionMutation(request("1", "null"))
        );
    }

    @Test
    void sameSiteNoneRequiresSecureCookieAndOnlyExactHttpsOrigins() {
        assertDoesNotThrow(() -> guard(
            "None", true, List.of("https://app.example.test"))
        );
        assertThrows(IllegalArgumentException.class, () -> guard(
            "None", false, List.of("https://app.example.test"))
        );
        assertThrows(IllegalArgumentException.class, () -> guard(
            "None", true, List.of("http://localhost:5173"))
        );
        assertThrows(IllegalArgumentException.class, () -> guard(
            "None", true, List.of("https://*.example.test"))
        );
        assertThrows(IllegalArgumentException.class, () -> guard(
            "None", true, List.of())
        );

        CorsProperties patternedCors = new CorsProperties();
        patternedCors.setAllowedOrigins(List.of("https://app.example.test"));
        patternedCors.setAllowedOriginPatterns(List.of("https://*.example.test"));
        assertThrows(IllegalArgumentException.class, () ->
            new AuthSessionRequestGuard(
                patternedCors,
                new RefreshTokenProperties(
                    Duration.ofDays(7), "canmakan_refresh", true, "None")
            )
        );
    }

    private static AuthSessionRequestGuard guard(
            String sameSite,
            boolean secure,
            List<String> origins) {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(origins);
        return new AuthSessionRequestGuard(
            corsProperties,
            new RefreshTokenProperties(
                Duration.ofDays(7), "canmakan_refresh", secure, sameSite)
        );
    }

    private static HttpServletRequest request(String intentHeader, String origin) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(AuthSessionRequestGuard.SESSION_REQUEST_HEADER))
            .thenReturn(intentHeader);
        when(request.getHeader(HttpHeaders.ORIGIN)).thenReturn(origin);
        return request;
    }
}
