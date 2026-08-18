package com.canmakan.backend.auth;

import com.canmakan.backend.auth.exception.AuthSessionRequestRejectedException;
import com.canmakan.backend.shared.security.CorsProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Protects endpoints that create, rotate, or revoke the refresh cookie.
 * The intent header is required for both browser and native clients. Browser
 * origins, when present, must also match the exact CORS origin allow-list.
 */
@Component
public final class AuthSessionRequestGuard {

    public static final String SESSION_REQUEST_HEADER = "X-CanMakan-Session-Request";
    private static final String SESSION_REQUEST_VALUE = "1";

    private final Set<String> trustedBrowserOrigins;

    public AuthSessionRequestGuard(
            CorsProperties corsProperties,
            RefreshTokenProperties refreshTokenProperties) {
        List<String> exactOrigins = corsProperties.getAllowedOrigins().stream()
            .map(origin -> origin.strip())
            .filter(origin -> !origin.isEmpty())
            .toList();
        this.trustedBrowserOrigins = exactOrigins.stream().collect(Collectors.toUnmodifiableSet());
        validateCrossSiteCookieDeployment(
            refreshTokenProperties,
            exactOrigins,
            corsProperties
        );
    }

    public void requireTrustedSessionMutation(HttpServletRequest request) {
        if (!SESSION_REQUEST_VALUE.equals(request.getHeader(SESSION_REQUEST_HEADER))) {
            throw new AuthSessionRequestRejectedException();
        }

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !trustedBrowserOrigins.contains(origin)) {
            throw new AuthSessionRequestRejectedException();
        }
    }

    private static void validateCrossSiteCookieDeployment(
            RefreshTokenProperties refreshTokenProperties,
            List<String> exactOrigins,
            CorsProperties corsProperties) {
        if (!"None".equals(refreshTokenProperties.cookieSameSite())) {
            return;
        }
        if (!refreshTokenProperties.cookieSecure()
                || !corsProperties.isAllowCredentials()
                || exactOrigins.isEmpty()
                || corsProperties.getAllowedOriginPatterns().stream()
                    .anyMatch(pattern -> !pattern.isBlank())
                || exactOrigins.stream().anyMatch(origin -> !isExactHttpsOrigin(origin))) {
            throw new IllegalArgumentException(
                "SameSite=None requires Secure credentialed CORS with exact HTTPS origins"
            );
        }
    }

    private static boolean isExactHttpsOrigin(String origin) {
        if (origin.contains("*") || "null".equalsIgnoreCase(origin)) {
            return false;
        }
        try {
            URI uri = URI.create(origin);
            return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && !uri.getHost().isBlank()
                && uri.getUserInfo() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
