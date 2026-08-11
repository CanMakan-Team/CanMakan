package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RefreshTokenPropertiesTest {

    @Test
    void acceptsValidRefreshConfiguration() {
        RefreshTokenProperties properties = new RefreshTokenProperties(
            Duration.ofDays(7),
            "canmakan_refresh",
            true
        );

        assertEquals(Duration.ofDays(7), properties.ttl());
        assertEquals("canmakan_refresh", properties.cookieName());
        assertTrue(properties.cookieSecure());
    }

    @Test
    void acceptsExplicitLocalHttpCookieConfiguration() {
        RefreshTokenProperties properties = new RefreshTokenProperties(
            Duration.ofMinutes(30),
            "refresh-token",
            false
        );

        assertFalse(properties.cookieSecure());
    }

    @Test
    void rejectsMissingOrUnsafeConfiguration() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new RefreshTokenProperties(null, "refresh", true)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RefreshTokenProperties(Duration.ZERO, "refresh", true)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RefreshTokenProperties(Duration.ofDays(1), "refresh token", true)
        );
    }
}
