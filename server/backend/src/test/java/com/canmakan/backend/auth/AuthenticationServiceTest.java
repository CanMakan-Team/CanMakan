package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthenticatedPrincipal;
import com.canmakan.backend.shared.security.JwtService;
import com.canmakan.backend.shared.security.SystemRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String PASSWORD_HASH = "$2a$10$test-password-hash";

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
            authenticationManager,
            jwtService,
            refreshTokenService
        );
    }

    @Test
    void authenticatesNormalizedEmailWithExactPasswordAndReturnsUserIdentity() {
        AuthUserDetails userDetails = userDetails(12L, "user@example.com", SystemRole.USER, true);
        when(authenticationManager.authenticate(any(Authentication.class)))
            .thenReturn(authenticated(userDetails));
        when(jwtService.issueAccessToken(12L)).thenReturn("signed-access-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.createSession(12L))
            .thenReturn(new IssuedRefreshToken("raw-refresh-token"));

        AuthenticationResult result = authenticationService.login(
            new LoginRequest("  USER@EXAMPLE.COM  ", "  Exact Password1!  ")
        );
        AuthResponse response = result.response();

        ArgumentCaptor<Authentication> authenticationCaptor =
            ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        assertEquals("user@example.com", authenticationCaptor.getValue().getPrincipal());
        assertEquals("  Exact Password1!  ", authenticationCaptor.getValue().getCredentials());
        assertEquals("signed-access-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresIn());
        assertEquals(new CurrentUserResponse(12L, "user@example.com", SystemRole.USER), response.user());
        assertEquals("raw-refresh-token", result.rawRefreshToken());
        verify(refreshTokenService).createSession(12L);
    }

    @Test
    void returnsServerDerivedAdminIdentity() {
        AuthUserDetails adminDetails = userDetails(1L, "admin@example.com", SystemRole.ADMIN, true);
        when(authenticationManager.authenticate(any(Authentication.class)))
            .thenReturn(authenticated(adminDetails));
        when(jwtService.issueAccessToken(1L)).thenReturn("admin-access-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.createSession(1L))
            .thenReturn(new IssuedRefreshToken("admin-refresh-token"));

        AuthResponse response = authenticationService.login(
            new LoginRequest("admin@example.com", "Password1!")
        ).response();

        assertEquals(SystemRole.ADMIN, response.user().role());
    }

    @Test
    void convertsCredentialAndDisabledFailuresToTheSameInternalSignal() {
        LoginRequest request = new LoginRequest("user@example.com", "Password1!");

        when(authenticationManager.authenticate(any(Authentication.class)))
            .thenThrow(new BadCredentialsException("wrong password"));
        assertThrows(AuthenticationFailedException.class, () -> authenticationService.login(request));

        when(authenticationManager.authenticate(any(Authentication.class)))
            .thenThrow(new DisabledException("inactive account"));
        assertThrows(AuthenticationFailedException.class, () -> authenticationService.login(request));
    }

    @Test
    void propagatesAuthenticationInfrastructureFailures() {
        LoginRequest request = new LoginRequest("user@example.com", "Password1!");
        InternalAuthenticationServiceException infrastructureFailure =
            new InternalAuthenticationServiceException("provider unavailable");
        when(authenticationManager.authenticate(any(Authentication.class)))
            .thenThrow(infrastructureFailure);

        InternalAuthenticationServiceException thrown = assertThrows(
            InternalAuthenticationServiceException.class,
            () -> authenticationService.login(request)
        );

        assertSame(infrastructureFailure, thrown);
    }

    @Test
    void refreshUsesCurrentRotationIdentityAndIssuesANewAccessToken() {
        AuthUserDetails currentAdmin = userDetails(12L, "user@example.com", SystemRole.ADMIN, true);
        when(refreshTokenService.rotate("old-refresh-token"))
            .thenReturn(new RefreshTokenRotation(
                currentAdmin,
                new IssuedRefreshToken("new-refresh-token")
            ));
        when(jwtService.issueAccessToken(12L)).thenReturn("new-access-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);

        AuthenticationResult result = authenticationService.refresh("old-refresh-token");

        assertEquals("new-access-token", result.response().accessToken());
        assertEquals(SystemRole.ADMIN, result.response().user().role());
        assertEquals("new-refresh-token", result.rawRefreshToken());
    }

    @Test
    void logoutDelegatesOnlyThePresentedRefreshCredentialForRevocation() {
        authenticationService.logout("presented-refresh-token");

        verify(refreshTokenService).revokeSession("presented-refresh-token");
    }

    private static Authentication authenticated(AuthUserDetails userDetails) {
        return new UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.getAuthorities()
        );
    }

    private static AuthUserDetails userDetails(
            Long userId,
            String email,
            SystemRole role,
            boolean active) {
        return new AuthUserDetails(
            new AuthenticatedPrincipal(userId, email, active, role),
            PASSWORD_HASH
        );
    }
}
