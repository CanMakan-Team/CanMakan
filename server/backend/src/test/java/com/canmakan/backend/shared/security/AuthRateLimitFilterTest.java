package com.canmakan.backend.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("AuthRateLimitFilter")
class AuthRateLimitFilterTest {

    private MockMvc mockMvc;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-17T00:00:00Z"));
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxAttempts(3);
        properties.setWindow(Duration.ofMinutes(1));
        AuthRateLimitFilter filter =
            new AuthRateLimitFilter(properties, new ObjectMapper(), clock);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .addFilters(filter)
            .build();
    }

    @Test
    @DisplayName("Allows requests within the limit")
    void allowsWithinLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Returns 429 after the limit is exceeded")
    void rejectsOverLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login").content("{}")).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/auth/login").content("{}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));
    }

    @Test
    @DisplayName("Resets after the window elapses")
    void resetsAfterWindow() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login").content("{}")).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/auth/login").content("{}"))
            .andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        mockMvc.perform(post("/api/auth/login").content("{}"))
            .andExpect(status().isOk());
    }

    @RestController
    @RequestMapping("/api/auth")
    static class ProbeController {
        @PostMapping("/login")
        ResponseEntity<String> login() {
            return ResponseEntity.ok("ok");
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
