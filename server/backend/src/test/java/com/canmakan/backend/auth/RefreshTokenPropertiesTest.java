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
            true,
            "None"
        );

        assertEquals(Duration.ofDays(7), properties.ttl());
        assertEquals("canmakan_refresh", properties.cookieName());
        assertTrue(properties.cookieSecure());
        assertEquals("None", properties.cookieSameSite());
    }

    @Test
    void acceptsExplicitLocalHttpCookieConfiguration() {
        RefreshTokenProperties properties = new RefreshTokenProperties(
            Duration.ofMinutes(30),
            "refresh-token",
            false,
            "Strict"
        );

        assertFalse(properties.cookieSecure());
        assertEquals("Strict", properties.cookieSameSite());
    }

    @Test
    void rejectsMissingOrUnsafeConfiguration() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new RefreshTokenProperties(null, "refresh", true, "None")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RefreshTokenProperties(Duration.ZERO, "refresh", true, "None")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RefreshTokenProperties(
                Duration.ofDays(1), "refresh token", true, "None")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RefreshTokenProperties(
                Duration.ofDays(1), "refresh", false, "None")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RefreshTokenProperties(
                Duration.ofDays(1), "refresh", true, "invalid")
        );
    }
}
