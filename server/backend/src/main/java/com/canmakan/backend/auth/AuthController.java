package com.canmakan.backend.auth;

import com.canmakan.backend.auth.dto.AuthResponse;
import com.canmakan.backend.auth.dto.AuthenticationResult;
import com.canmakan.backend.auth.dto.CurrentUserResponse;
import com.canmakan.backend.auth.dto.LoginRequest;
import com.canmakan.backend.auth.dto.RegistrationRequest;
import com.canmakan.backend.auth.dto.RegistrationResponse;
import com.canmakan.backend.auth.exception.RefreshAuthenticationException;
import com.canmakan.backend.shared.security.AuthUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC19 login, refresh, logout, and current-account endpoints. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService refreshCookieService;
    private final AuthSessionRequestGuard sessionRequestGuard;

    // User login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        sessionRequestGuard.requireTrustedSessionMutation(servletRequest);
        return authenticationResponse(authService.login(request));
    }

    // User refresh token
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        sessionRequestGuard.requireTrustedSessionMutation(request);
        try {
            String rawRefreshToken = refreshCookieService.readRefreshToken(request)
                .orElseThrow(RefreshAuthenticationException::new);
            return authenticationResponse(authService.refresh(rawRefreshToken));
        } catch (RefreshAuthenticationException exception) {
            response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookieService.clearRefreshCookie().toString()
            );
            throw exception;
        }
    }

    // User logout
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        sessionRequestGuard.requireTrustedSessionMutation(request);
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshCookieService.clearRefreshCookie().toString()
        );
        authService.logout(
            refreshCookieService.readRefreshToken(request).orElse(null)
        );
        return ResponseEntity.noContent().build();
    }

    // Get current user details
    @GetMapping("/me")
    public CurrentUserResponse currentUser(
            @AuthenticationPrincipal AuthUserDetails userDetails) {
        return CurrentUserResponse.from(userDetails);
    }

    private ResponseEntity<AuthResponse> authenticationResponse(AuthenticationResult result) {
        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookieService.createRefreshCookie(result.rawRefreshToken()).toString()
            )
            .body(result.response());
    }

    // User registration
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(authService.register(request));
    }

}
