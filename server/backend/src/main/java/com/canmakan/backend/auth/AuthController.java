package com.canmakan.backend.auth;

import com.canmakan.backend.shared.security.AuthUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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

    private final AuthenticationService authenticationService;
    private final RefreshCookieService refreshCookieService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return authenticationResponse(authenticationService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String rawRefreshToken = refreshCookieService.readRefreshToken(request)
            .orElseThrow(RefreshAuthenticationException::new);
        return authenticationResponse(authenticationService.refresh(rawRefreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshCookieService.clearRefreshCookie().toString()
        );
        authenticationService.logout(
            refreshCookieService.readRefreshToken(request).orElse(null)
        );
        return ResponseEntity.noContent().build();
    }

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
}
