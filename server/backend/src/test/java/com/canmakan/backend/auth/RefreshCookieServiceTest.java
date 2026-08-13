package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

class RefreshCookieServiceTest {

    private static final String COOKIE_NAME = "canmakan_refresh";

    @Test
    void createsSecureNarrowlyScopedHttpOnlyCrossSiteCookie() {
        RefreshCookieService service = service(true);

        ResponseCookie cookie = service.createRefreshCookie("raw-refresh-token");

        assertEquals(COOKIE_NAME, cookie.getName());
        assertEquals("raw-refresh-token", cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("None", cookie.getSameSite());
        assertEquals("/api/auth", cookie.getPath());
        assertEquals(Duration.ofDays(7), cookie.getMaxAge());
        assertNull(cookie.getDomain());
    }

    @Test
    void supportsExplicitNonSecureCookieForLocalHttpTestsOnly() {
        assertFalse(service(false).createRefreshCookie("raw-refresh-token").isSecure());
    }

    @Test
    void clearsTheSameCookieWithImmediateExpiryAndMatchingSecurityAttributes() {
        ResponseCookie cookie = service(true).clearRefreshCookie();

        assertEquals(COOKIE_NAME, cookie.getName());
        assertEquals("", cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("None", cookie.getSameSite());
        assertEquals("/api/auth", cookie.getPath());
        assertEquals(Duration.ZERO, cookie.getMaxAge());
        assertNull(cookie.getDomain());
    }

    @Test
    void readsOnlyOneDynamicallyNamedRefreshCookie() {
        RefreshCookieService service = service(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
            new Cookie("unrelated", "value"),
            new Cookie(COOKIE_NAME, "raw-refresh-token")
        );

        assertEquals("raw-refresh-token", service.readRefreshToken(request).orElseThrow());
    }

    @Test
    void missingOrDuplicateRefreshCookiesFailClosed() {
        RefreshCookieService service = service(true);
        MockHttpServletRequest missing = new MockHttpServletRequest();
        MockHttpServletRequest duplicate = new MockHttpServletRequest();
        duplicate.setCookies(
            new Cookie(COOKIE_NAME, "first"),
            new Cookie(COOKIE_NAME, "second")
        );

        assertTrue(service.readRefreshToken(missing).isEmpty());
        assertTrue(service.readRefreshToken(duplicate).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.createRefreshCookie(" "));
    }

    private static RefreshCookieService service(boolean secure) {
        return new RefreshCookieService(new RefreshTokenProperties(
            Duration.ofDays(7),
            COOKIE_NAME,
            secure,
            secure ? "None" : "Strict"
        ));
    }

}
