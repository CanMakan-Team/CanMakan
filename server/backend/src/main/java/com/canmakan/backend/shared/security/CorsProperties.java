package com.canmakan.backend.shared.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * CORS allow-list for browser clients (Vite web) and any Origin-sending clients
 * on the local network (physical-device / LAN testing).
 *
 * Native Android Retrofit calls typically omit {@code Origin}; CORS does not
 * block them. These settings still matter for the web app and for WebViews.
 *
 * <p>Values come from {@code canmakan.cors.*} and are overridable at deploy time
 * via environment variables (comma-separated lists):
 * {@code CANMAKAN_CORS_ALLOWED_ORIGINS}, {@code CANMAKAN_CORS_ALLOWED_ORIGIN_PATTERNS},
 * {@code CANMAKAN_CORS_ALLOW_CREDENTIALS}, {@code CANMAKAN_CORS_MAX_AGE_SECONDS}.
 *
 * @author Amelia
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "canmakan.cors")
public class CorsProperties {

    /**
     * Exact origins (scheme + host + port), e.g. Vite {@code http://localhost:5173}
     * or production {@code https://app.example.com}.
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * Origin patterns for LAN / alternate ports, e.g. {@code http://192.168.*.*:[*]}.
     * Leave empty in production when only exact public origins are allowed.
     */
    private List<String> allowedOriginPatterns = new ArrayList<>();

    /**
     * Allowed methods for the CORS configuration.
     * 
     * NOTE: DELETE only for soft deletes
     */
    private List<String> allowedMethods = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE");

    /**
     * Allowed headers for the CORS configuration.
     */
    private List<String> allowedHeaders = List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Requested-With",
            "X-CanMakan-Session-Request");

    /**
     * Exposed headers for the CORS configuration.
     */
    private List<String> exposedHeaders = List.of("Authorization");

    /** Browser credentialed mode required by the HttpOnly refresh cookie. */
    private boolean allowCredentials = true;

    private long maxAgeSeconds = 3600;
}
