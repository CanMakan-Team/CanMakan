package com.canmakan.backend.shared.security;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * In-memory rate limits for public auth endpoints (login / register / refresh).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.auth-rate-limit")
public class AuthRateLimitProperties {

    /** When false, the filter is a no-op (useful in some integration tests). */
    private boolean enabled = true;

    /** Max attempts per client key within the window. */
    private int maxAttempts = 30;

    /** Sliding window length. */
    private Duration window = Duration.ofMinutes(1);
}
