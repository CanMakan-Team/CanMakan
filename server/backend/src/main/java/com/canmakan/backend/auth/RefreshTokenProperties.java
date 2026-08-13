package com.canmakan.backend.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Validated configuration for UC19 refresh sessions and their cookie. */
@ConfigurationProperties(prefix = "app.security.refresh")
public final class RefreshTokenProperties {

    private final Duration ttl;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public RefreshTokenProperties(
            Duration ttl,
            String cookieName,
            boolean cookieSecure,
            String cookieSameSite) {
        if (ttl == null || ttl.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
        if (cookieName == null || !cookieName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Refresh cookie name is invalid");
        }
        String normalizedSameSite = normalizeSameSite(cookieSameSite);
        if ("None".equals(normalizedSameSite) && !cookieSecure) {
            throw new IllegalArgumentException(
                "SameSite=None refresh cookies must be Secure"
            );
        }
        this.ttl = ttl;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = normalizedSameSite;
    }

    public Duration ttl() {
        return ttl;
    }

    public String cookieName() {
        return cookieName;
    }

    public boolean cookieSecure() {
        return cookieSecure;
    }

    public String cookieSameSite() {
        return cookieSameSite;
    }

    private static String normalizeSameSite(String sameSite) {
        if (sameSite == null) {
            throw new IllegalArgumentException("Refresh cookie SameSite mode is required");
        }
        return switch (sameSite.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            case "none" -> "None";
            default -> throw new IllegalArgumentException(
                "Refresh cookie SameSite mode must be Strict, Lax, or None"
            );
        };
    }
}
