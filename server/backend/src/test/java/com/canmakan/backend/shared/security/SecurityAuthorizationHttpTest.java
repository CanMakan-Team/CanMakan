package com.canmakan.backend.shared.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Database-free HTTP tests for the UC19 authorization matcher contract. */
class SecurityAuthorizationHttpTest {

    private static final String USER_TOKEN = "active-user-token";
    private static final String ADMIN_TOKEN = "active-admin-token";
    private static final String INACTIVE_TOKEN = "inactive-user-token";

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertyValues.of(
            "app.security.jwt.issuer=canmakan-test",
            "app.security.jwt.access-ttl=15m",
            "app.security.jwt.signing-secret=dGVzdC1vbmx5LXNpZ25pbmcta2V5LTMyLWJ5dGVzISE=",
            "app.security.refresh.ttl=7d",
            "app.security.refresh.cookie-name=canmakan_refresh",
            "app.security.refresh.cookie-secure=false",
            "app.security.refresh.cookie-same-site=Lax"
        ).applyTo(context);
        context.register(HarnessConfiguration.class);
        context.refresh();

        JwtService jwtService = context.getBean(JwtService.class);
        AuthUserDetailsService userDetailsService = context.getBean(AuthUserDetailsService.class);
        when(jwtService.extractUserId(USER_TOKEN)).thenReturn(12L);
        when(jwtService.extractUserId(ADMIN_TOKEN)).thenReturn(1L);
        when(jwtService.extractUserId(INACTIVE_TOKEN)).thenReturn(13L);
        when(jwtService.extractUserId("malformed-token"))
            .thenThrow(new BadJwtException("safe test failure"));
        when(jwtService.extractUserId("expired-token"))
            .thenThrow(new BadJwtException("safe test failure"));
        when(userDetailsService.loadUserById(12L))
            .thenReturn(userDetails(12L, true, SystemRole.USER));
        when(userDetailsService.loadUserById(1L))
            .thenReturn(userDetails(1L, true, SystemRole.ADMIN));
        when(userDetailsService.loadUserById(13L))
            .thenReturn(userDetails(13L, false, SystemRole.USER));

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
            .build();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void scanValidationRejectsMissingMalformedExpiredAndInactiveCredentials() throws Exception {
        assertUnauthorized(post("/api/scan/validate"));
        assertUnauthorized(post("/api/scan/validate")
            .header(HttpHeaders.AUTHORIZATION, bearer("malformed-token")));
        assertUnauthorized(post("/api/scan/validate")
            .header(HttpHeaders.AUTHORIZATION, bearer("expired-token")));
        assertUnauthorized(post("/api/scan/validate")
            .header(HttpHeaders.AUTHORIZATION, bearer(INACTIVE_TOKEN)));
    }

    @Test
    void activeUserAndAdminCanReachAuthenticatedScanValidation() throws Exception {
        mockMvc.perform(post("/api/scan/validate")
                .header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/scan/validate")
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
            .andExpect(status().isOk());
    }

    @Test
    void adminRoutesRemainAdminOnly() throws Exception {
        assertUnauthorized(get("/api/admin/harness"));
        mockMvc.perform(get("/api/admin/harness")
                .header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Access denied."));
        mockMvc.perform(get("/api/admin/harness")
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
            .andExpect(status().isOk());
    }

    @Test
    void familyProfileAndUnlistedBusinessRoutesAreNeverAnonymous() throws Exception {
        assertUnauthorized(get("/api/families/me"));
        assertUnauthorized(get("/api/profiles/42/recommendations"));
        assertUnauthorized(get("/api/new-business-route"));

        mockMvc.perform(get("/api/new-business-route")
                .header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
            .andExpect(status().isOk());
    }

    @Test
    void explicitPublicAllowListRemainsAnonymous() throws Exception {
        mockMvc.perform(post("/api/auth/register")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isOk());
        mockMvc.perform(get("/api/invitations/invite-token/preview"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")
                .header(HttpHeaders.AUTHORIZATION, bearer("malformed-token")))
            .andExpect(status().isOk());
    }

    @Test
    void corsPreflightRemainsPermitted() throws Exception {
        mockMvc.perform(options("/api/scan/validate")
                .header(HttpHeaders.ORIGIN, "https://app.example.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
            .andExpect(status().isOk())
            .andExpect(header().string(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                "https://app.example.test"
            ));
    }

    @Test
    void nonApiFallbackIsDeniedEvenWhenAuthenticated() throws Exception {
        assertUnauthorized(get("/internal/harness"));
        mockMvc.perform(get("/internal/harness")
                .header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Access denied."));
    }

    private void assertUnauthorized(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    private static AuthUserDetails userDetails(long id, boolean active, SystemRole role) {
        return new AuthUserDetails(
            new AuthenticatedPrincipal(id, "account" + id + "@example.com", active, role),
            "{noop}unused"
        );
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
    })
    static class HarnessConfiguration {

        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        AuthUserDetailsService authUserDetailsService() {
            return mock(AuthUserDetailsService.class);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(
                JwtService jwtService,
                AuthUserDetailsService userDetailsService,
                RestAuthenticationEntryPoint authenticationEntryPoint) {
            return new JwtAuthenticationFilter(
                jwtService,
                userDetailsService,
                authenticationEntryPoint
            );
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(List.of("https://app.example.test"));
            cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
            cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            cors.setAllowCredentials(true);
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", cors);
            return source;
        }

        @Bean
        HarnessController harnessController() {
            return new HarnessController();
        }
    }

    @RestController
    static class HarnessController {

        @PostMapping({
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/scan/validate"
        })
        void postEndpoint() {
        }

        @GetMapping({
            "/api/invitations/{token}/preview",
            "/actuator/health",
            "/api/admin/harness",
            "/api/families/me",
            "/api/profiles/42/recommendations",
            "/api/new-business-route",
            "/internal/harness"
        })
        void getEndpoint() {
        }
    }
}
