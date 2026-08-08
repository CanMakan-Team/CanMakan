package com.canmakan.backend.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Validates Bearer JWTs and reloads the current account before authentication. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> JWT_INDEPENDENT_POST_PATHS = Set.of(
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/logout"
    );
    private static final String HEALTH_PATH = "/actuator/health";
    private static final String SERVER_ERROR_RESPONSE =
        "{\"message\":\"Authentication request could not be completed.\"}";

    private final JwtService jwtService;
    private final AuthUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AuthUserDetailsService userDetailsService,
            RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return (HttpMethod.POST.matches(request.getMethod())
                && JWT_INDEPENDENT_POST_PATHS.contains(requestPath))
            || (HttpMethod.GET.matches(request.getMethod())
                && HEALTH_PATH.equals(requestPath));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = resolveBearerToken(authorization);
            Long userId = jwtService.extractUserId(token);
            AuthUserDetails userDetails = userDetailsService.loadUserById(userId);
            if (!userDetails.isEnabled()) {
                throw new BadCredentialsException("Account is unavailable");
            }

            userDetails.eraseCredentials();
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
                );
            authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
            );
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
        } catch (JwtException | BadCredentialsException | UsernameNotFoundException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                request,
                response,
                new BadCredentialsException("Bearer authentication failed")
            );
            return;
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            writeServerError(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String resolveBearerToken(String authorization) {
        if (authorization.length() <= BEARER_PREFIX.length()
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new BadCredentialsException("Invalid Authorization header");
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        if (token.isBlank() || token.chars().anyMatch(Character::isWhitespace)) {
            throw new BadCredentialsException("Invalid Bearer token");
        }
        return token;
    }

    private static void writeServerError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(SERVER_ERROR_RESPONSE);
    }
}
