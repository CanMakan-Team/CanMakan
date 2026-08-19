package com.canmakan.backend.shared.security;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import lombok.RequiredArgsConstructor;

/**
 * Applies {@link CorsProperties} for browser clients (web Vite) and LAN origins.
 * Uses a high-precedence {@link CorsFilter} so preflight {@code OPTIONS} requests
 * succeed before controllers run. Native Android Retrofit typically omits Origin
 * and is unaffected by CORS checks.
 *
 * <p>Allow-lists are environment-configurable ({@code CANMAKAN_CORS_ALLOWED_ORIGINS}
 * and related properties) so local defaults stay in place while production can
 * inject public web origins without rebuilding.
 *
 * @author Amelia
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private final CorsProperties corsProperties;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = nonBlankEntries(corsProperties.getAllowedOrigins());
        if (!CollectionUtils.isEmpty(allowedOrigins)) {
            configuration.setAllowedOrigins(allowedOrigins);
        }
        List<String> allowedOriginPatterns = nonBlankEntries(corsProperties.getAllowedOriginPatterns());
        if (!CollectionUtils.isEmpty(allowedOriginPatterns)) {
            configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        }
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setExposedHeaders(corsProperties.getExposedHeaders());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /** Drops blank tokens from comma-separated env overrides (e.g. trailing commas). */
    private static List<String> nonBlankEntries(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return values.stream()
            .filter(value -> StringUtils.hasText(value))
            .map(value -> value.trim())
            .toList();
    }

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilterRegistration(CorsConfigurationSource corsConfigurationSource) {
        FilterRegistrationBean<CorsFilter> registration =
            new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        // Map broadly; CorsConfigurationSource only applies rules to /api/** 
        registration.addUrlPatterns("/*");
        return registration;
    }
}