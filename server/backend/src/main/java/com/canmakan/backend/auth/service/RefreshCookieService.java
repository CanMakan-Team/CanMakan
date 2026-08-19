package com.canmakan.backend.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.canmakan.backend.auth.config.RefreshTokenProperties;

/** Creates and reads the narrowly scoped UC19 refresh credential cookie. */
@Service
@RequiredArgsConstructor
public class RefreshCookieService {

    private static final String COOKIE_PATH = "/api/auth";

    private final RefreshTokenProperties properties;

    public ResponseCookie createRefreshCookie(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new IllegalArgumentException("rawRefreshToken is required");
        }
        return buildRefreshCookie(rawRefreshToken, properties.ttl());
    }

    public ResponseCookie clearRefreshCookie() {
        return buildRefreshCookie("", Duration.ZERO);
    }

    private ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.cookieName(), value)
            .httpOnly(true)
            .secure(properties.cookieSecure())
            .sameSite(properties.cookieSameSite())
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .build();
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        String rawToken = null;
        for (Cookie cookie : cookies) {
            if (properties.cookieName().equals(cookie.getName())) {
                if (rawToken != null) {
                    return Optional.empty();
                }
                rawToken = cookie.getValue();
            }
        }
        return Optional.ofNullable(rawToken);
    }
}
