package com.canmakan.backend.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Validated configuration for UC19 refresh sessions and their cookie. */
@ConfigurationProperties(prefix = "app.security.refresh")
public final class RefreshTokenProperties {

    private final Duration ttl;
    private final String cookieName;
    private final boolean cookieSecure;

    public RefreshTokenProperties(Duration ttl, String cookieName, boolean cookieSecure) {
        if (ttl == null || ttl.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
        if (cookieName == null || !cookieName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Refresh cookie name is invalid");
        }
        this.ttl = ttl;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
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
}
