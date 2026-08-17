package com.canmakan.backend.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Simple in-memory rate limiter for public auth mutation endpoints.
 *
 * <p>Keys by client IP and path. Sufficient for a single-instance backend;
 * replace with a distributed store if the API is horizontally scaled.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
        "/api/auth/login",
        "/api/auth/register",
        "/api/auth/refresh");

    private final AuthRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimitFilter(AuthRateLimitProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    AuthRateLimitFilter(AuthRateLimitProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !LIMITED_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String key = clientKey(request) + "|" + request.getRequestURI();
        long nowMs = clock.millis();
        long windowMs = Math.max(1L, properties.getWindow().toMillis());
        int maxAttempts = Math.max(1, properties.getMaxAttempts());

        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || nowMs - existing.windowStartMs >= windowMs) {
                return new WindowCounter(nowMs);
            }
            return existing;
        });

        int attempt = counter.count.incrementAndGet();
        if (attempt > maxAttempts) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                response.getOutputStream(),
                Map.of("message", "Too many requests. Please try again later."));
            return;
        }

        // Opportunistic cleanup of stale keys (bounded growth on idle IPs).
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(e -> nowMs - e.getValue().windowStartMs >= windowMs * 2);
        }

        filterChain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    private static final class WindowCounter {
        private final long windowStartMs;
        private final AtomicInteger count = new AtomicInteger(0);

        private WindowCounter(long windowStartMs) {
            this.windowStartMs = windowStartMs;
        }
    }
}
