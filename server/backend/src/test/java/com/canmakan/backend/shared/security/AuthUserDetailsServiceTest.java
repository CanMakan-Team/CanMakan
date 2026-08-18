package com.canmakan.backend.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.user.repository.AuthenticationAccountView;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class AuthUserDetailsServiceTest {

    private static final String PASSWORD_HASH = "$2a$10$test-password-hash";

    @Mock
    private UserAccountRepository userAccountRepository;

    private AuthUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new AuthUserDetailsService(userAccountRepository);
    }

    @Test
    void normalizesEmailAndBuildsUserPrincipal() {
        AuthenticationAccountView account = account(
            14L,
            "person@example.com",
            true,
            "USER"
        );
        when(userAccountRepository.findAuthenticationAccountByEmail("person@example.com"))
            .thenReturn(Optional.of(account));

        AuthUserDetails userDetails = (AuthUserDetails) userDetailsService
            .loadUserByUsername("  Person@Example.COM  ");

        verify(userAccountRepository).findAuthenticationAccountByEmail("person@example.com");
        assertEquals(14L, userDetails.getUserId());
        assertEquals("person@example.com", userDetails.getUsername());
        assertEquals(SystemRole.USER, userDetails.getSystemRole());
        assertEquals(
            "ROLE_USER",
            userDetails.getAuthorities().iterator().next().getAuthority()
        );
        assertTrue(userDetails.isEnabled());
        assertEquals(PASSWORD_HASH, userDetails.getPassword());
    }

    @Test
    void mapsAdminRoleToRoleAdmin() {
        AuthenticationAccountView account = account(
            3L,
            "admin@example.com",
            true,
            "ADMIN"
        );
        when(userAccountRepository.findAuthenticationAccountByEmail("admin@example.com"))
            .thenReturn(Optional.of(account));

        AuthUserDetails userDetails = (AuthUserDetails) userDetailsService
            .loadUserByUsername("admin@example.com");

        assertEquals(SystemRole.ADMIN, userDetails.getSystemRole());
        assertEquals(
            "ROLE_ADMIN",
            userDetails.getAuthorities().iterator().next().getAuthority()
        );
    }

    @Test
    void reloadsCurrentAuthenticationAccountByJwtUserId() {
        AuthenticationAccountView account = account(
            14L,
            "person@example.com",
            true,
            "ADMIN"
        );
        when(userAccountRepository.findAuthenticationAccountById(14L))
            .thenReturn(Optional.of(account));

        AuthUserDetails userDetails = userDetailsService.loadUserById(14L);

        verify(userAccountRepository).findAuthenticationAccountById(14L);
        assertEquals(14L, userDetails.getUserId());
        assertEquals(SystemRole.ADMIN, userDetails.getSystemRole());
    }

    @Test
    void representsInactiveAccountAsDisabled() {
        AuthenticationAccountView account = account(
            20L,
            "inactive@example.com",
            false,
            "USER"
        );
        when(userAccountRepository.findAuthenticationAccountByEmail("inactive@example.com"))
            .thenReturn(Optional.of(account));

        AuthUserDetails userDetails = (AuthUserDetails) userDetailsService
            .loadUserByUsername("inactive@example.com");

        assertFalse(userDetails.isEnabled());
        assertFalse(userDetails.getAuthenticatedPrincipal().active());
    }

    @Test
    void rejectsUnknownDatabaseRole() {
        AuthenticationAccountView account = account(
            14L,
            "person@example.com",
            true,
            "ROLE_APP_USER"
        );
        when(userAccountRepository.findAuthenticationAccountByEmail("person@example.com"))
            .thenReturn(Optional.of(account));

        assertThrows(
            UsernameNotFoundException.class,
            () -> userDetailsService.loadUserByUsername("person@example.com")
        );
    }

    @Test
    void rejectsAccountRecordMissingAnEmail() {
        AuthenticationAccountView account = account(
            14L,
            null,
            true,
            "USER"
        );
        when(userAccountRepository.findAuthenticationAccountByEmail("person@example.com"))
            .thenReturn(Optional.of(account));

        assertThrows(
            UsernameNotFoundException.class,
            () -> userDetailsService.loadUserByUsername("person@example.com")
        );
    }

    @Test
    void erasesPasswordHashFromUserDetails() {
        AuthenticationAccountView account = account(
            14L,
            "person@example.com",
            true,
            "USER"
        );
        when(userAccountRepository.findAuthenticationAccountByEmail("person@example.com"))
            .thenReturn(Optional.of(account));
        AuthUserDetails userDetails = (AuthUserDetails) userDetailsService
            .loadUserByUsername("person@example.com");

        userDetails.eraseCredentials();

        assertNull(userDetails.getPassword());
    }

    private static AuthenticationAccountView account(
            Long userId,
            String email,
            boolean active,
            String roleName) {
        return new TestAuthenticationAccount(
            userId,
            email,
            PASSWORD_HASH,
            active,
            roleName
        );
    }

    private record TestAuthenticationAccount(
        Long userId,
        String email,
        String passwordHash,
        Boolean active,
        String roleName
    ) implements AuthenticationAccountView {

        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public String getPasswordHash() {
            return passwordHash;
        }

        @Override
        public Boolean getActive() {
            return active;
        }

        @Override
        public String getRoleName() {
            return roleName;
        }
    }
}
