package com.canmakan.backend.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
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
    void lockedAccountIsRejectedAfterCredentialVerification() {
        assertThrows(
            LockedException.class,
            () -> SecurityConfig.rejectUnavailableAccount(stubUser("locked@example.com", false, true, true, true))
        );
    }

    @Test
    void expiredAccountIsRejectedAfterCredentialVerification() {
        assertThrows(
            AccountExpiredException.class,
            () -> SecurityConfig.rejectUnavailableAccount(stubUser("expired@example.com", true, true, false, true))
        );
    }

    @Test
    void expiredCredentialsAreRejectedAfterCredentialVerification() {
        assertThrows(
            CredentialsExpiredException.class,
            () -> SecurityConfig.rejectUnavailableAccount(stubUser("stale@example.com", true, true, true, false))
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

    /**
     * UserDetails that can fail each post-auth check independently. AuthUserDetails always
     * reports non-locked, non-expired account and credentials, so those branches need a stub.
     */
    private UserDetails stubUser(
            String email,
            boolean accountNonLocked,
            boolean enabled,
            boolean accountNonExpired,
            boolean credentialsNonExpired) {
        String passwordHash = passwordEncoder.encode(PASSWORD);
        return new UserDetails() {
            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return List.of();
            }

            @Override
            public String getPassword() {
                return passwordHash;
            }

            @Override
            public String getUsername() {
                return email;
            }

            @Override
            public boolean isAccountNonExpired() {
                return accountNonExpired;
            }

            @Override
            public boolean isAccountNonLocked() {
                return accountNonLocked;
            }

            @Override
            public boolean isCredentialsNonExpired() {
                return credentialsNonExpired;
            }

            @Override
            public boolean isEnabled() {
                return enabled;
            }
        };
    }
}
