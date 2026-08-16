package com.canmakan.backend.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigTest {

    private static final String PASSWORD = "Exact Password1!";

    private AuthUserDetailsService userDetailsService;
    private PasswordEncoder passwordEncoder;
    private AuthenticationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        SecurityConfig securityConfig = new SecurityConfig();
        userDetailsService = mock(AuthUserDetailsService.class);
        passwordEncoder = securityConfig.passwordEncoder();
        authenticationProvider = securityConfig.authenticationProvider(
            userDetailsService,
            passwordEncoder
        );
    }

    @Test
    void wrongPasswordForSuspendedAccountRemainsGenericBadCredentials() {
        when(userDetailsService.loadUserByUsername("inactive@example.com"))
            .thenReturn(userDetails(12L, false, SystemRole.USER));

        assertThrows(
            BadCredentialsException.class,
            () -> authenticate("inactive@example.com", "Wrong Password1!")
        );
    }

    @Test
    void correctPasswordForSuspendedAccountIsRejectedAfterCredentialVerification() {
        when(userDetailsService.loadUserByUsername("inactive@example.com"))
            .thenReturn(userDetails(12L, false, SystemRole.USER));

        assertThrows(
            DisabledException.class,
            () -> authenticate("inactive@example.com", PASSWORD)
        );
    }

    @Test
    void activeUserAndAdminCredentialsStillAuthenticate() {
        when(userDetailsService.loadUserByUsername("user@example.com"))
            .thenReturn(userDetails(12L, true, SystemRole.USER));
        when(userDetailsService.loadUserByUsername("admin@example.com"))
            .thenReturn(userDetails(1L, true, SystemRole.ADMIN));

        Authentication user = authenticate("user@example.com", PASSWORD);
        Authentication admin = authenticate("admin@example.com", PASSWORD);

        assertEquals(SystemRole.USER, assertInstanceOf(
            AuthUserDetails.class,
            user.getPrincipal()
        ).getSystemRole());
        assertEquals(SystemRole.ADMIN, assertInstanceOf(
            AuthUserDetails.class,
            admin.getPrincipal()
        ).getSystemRole());
    }

    private Authentication authenticate(String email, String password) {
        return authenticationProvider.authenticate(
            new UsernamePasswordAuthenticationToken(email, password)
        );
    }

    private AuthUserDetails userDetails(long userId, boolean active, SystemRole role) {
        return new AuthUserDetails(
            new AuthenticatedPrincipal(userId, role.name().toLowerCase() + "@example.com", active, role),
            passwordEncoder.encode(PASSWORD)
        );
    }
}
