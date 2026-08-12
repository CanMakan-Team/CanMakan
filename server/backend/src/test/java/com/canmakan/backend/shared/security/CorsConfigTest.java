package com.canmakan.backend.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CorsFilter;

/**
 * Verifies Vite and LAN origins receive CORS headers on /api/**.
 */
class CorsConfigTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(java.util.List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173"));
        properties.setAllowedOriginPatterns(java.util.List.of(
                "http://10.*.*.*:[*]",
                "http://192.168.*.*:[*]"));

        CorsConfigurationSourceHolder holder = new CorsConfigurationSourceHolder(properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new CorsFilter(holder.source()))
                .build();
    }

    @Test
    @DisplayName("Vite localhost origin is allowed on preflight")
    void viteOriginAllowedOnPreflight() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                            "content-type,x-canmakan-session-request"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsString("x-canmakan-session-request")))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
    }

    @Test
    @DisplayName("Android emulator host LAN pattern is allowed on preflight")
    void lanOriginAllowedOnPreflight() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://192.168.1.50:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://192.168.1.50:5173"));
    }

    @Test
    @DisplayName("Actual POST from Vite origin echoes Allow-Origin")
    void actualRequestIncludesAllowOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("Disallowed origin does not receive Allow-Origin")
    void disallowedOriginRejected() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    /** Builds the same CorsConfigurationSource shape as {@link CorsConfig}. */
    private static final class CorsConfigurationSourceHolder {
        private final org.springframework.web.cors.CorsConfigurationSource source;

        private CorsConfigurationSourceHolder(CorsProperties properties) {
            this.source = new CorsConfig(properties).corsConfigurationSource();
        }

        private org.springframework.web.cors.CorsConfigurationSource source() {
            return source;
        }
    }

    @RestController
    @RequestMapping("/api/auth")
    static class ProbeController {
        @PostMapping("/login")
        ResponseEntity<String> login() {
            return ResponseEntity.ok("ok");
        }

        @PostMapping("/register")
        ResponseEntity<String> register() {
            return ResponseEntity.ok("ok");
        }
    }
}
