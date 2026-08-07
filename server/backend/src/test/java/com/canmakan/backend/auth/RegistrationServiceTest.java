package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC18: RegistrationService")
class RegistrationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    private PasswordEncoder passwordEncoder;
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(10);
        registrationService = new RegistrationService(userAccountRepository, passwordEncoder);
    }

    @Test
    @DisplayName("UC18 BE1: creates an active USER account with a normalized email and BCrypt hash")
    void createsActiveUserWithNormalizedEmailAndBcryptHash() {
        String rawPassword = "  KeepCase Password1!  ";
        RegistrationRequest request = new RegistrationRequest(
            "  Person@Example.COM  ",
            rawPassword
        );
        when(userAccountRepository.existsByEmail("person@example.com")).thenReturn(false);
        when(userAccountRepository.findRoleIdByName("USER")).thenReturn(Optional.of(2L));
        when(userAccountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId(14L);
            return account;
        });

        RegistrationResponse response = registrationService.register(request);

        ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).saveAndFlush(accountCaptor.capture());
        UserAccount persisted = accountCaptor.getValue();

        assertEquals("person@example.com", persisted.getEmail());
        assertEquals(2L, persisted.getRoleId());
        assertTrue(persisted.isActive());
        assertNotEquals(rawPassword, persisted.getPasswordHash());
        assertTrue(passwordEncoder.matches(rawPassword, persisted.getPasswordHash()));
        assertFalse(passwordEncoder.matches(rawPassword.trim(), persisted.getPasswordHash()));
        assertTrue(persisted.getPasswordHash().matches("^\\$2[aby]\\$10\\$.*"));
        assertFalse(persisted.toString().contains(persisted.getPasswordHash()));
        assertFalse(request.toString().contains(rawPassword));

        assertEquals(14L, response.userId());
        assertEquals("person@example.com", response.email());
        assertTrue(response.active());
        verify(userAccountRepository).findRoleIdByName(RegistrationService.PUBLIC_REGISTRATION_ROLE);
    }

    @Test
    @DisplayName("UC18 BE2: reports a friendly conflict before insert when email already exists")
    void rejectsExistingEmailBeforeInsert() {
        RegistrationRequest request = new RegistrationRequest("person@example.com", "Password1!");
        when(userAccountRepository.existsByEmail("person@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> registrationService.register(request));

        verify(userAccountRepository, never()).findRoleIdByName(any());
        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UC18 BE3: translates a concurrent email UNIQUE race into duplicate conflict")
    void translatesConcurrentDuplicateInsert() {
        RegistrationRequest request = new RegistrationRequest("person@example.com", "Password1!");
        when(userAccountRepository.existsByEmail("person@example.com")).thenReturn(false);
        when(userAccountRepository.findRoleIdByName("USER")).thenReturn(Optional.of(2L));
        when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
            .thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new SQLIntegrityConstraintViolationException("Duplicate entry", "23000", 1062)
            ));

        assertThrows(DuplicateEmailException.class, () -> registrationService.register(request));
    }

    @Test
    @DisplayName("UC18 BE4: non-duplicate integrity failures remain controlled server errors")
    void doesNotMisreportOtherIntegrityFailuresAsDuplicateEmail() {
        RegistrationRequest request = new RegistrationRequest("person@example.com", "Password1!");
        when(userAccountRepository.existsByEmail("person@example.com")).thenReturn(false);
        when(userAccountRepository.findRoleIdByName("USER")).thenReturn(Optional.of(2L));
        when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
            .thenThrow(new DataIntegrityViolationException("unexpected integrity failure"));

        assertThrows(RegistrationFailedException.class, () -> registrationService.register(request));
    }

    @Test
    @DisplayName("UC18 BE5: missing USER role is a controlled configuration failure")
    void missingUserRoleIsControlledFailure() {
        RegistrationRequest request = new RegistrationRequest("person@example.com", "Password1!");
        when(userAccountRepository.existsByEmail("person@example.com")).thenReturn(false);
        when(userAccountRepository.findRoleIdByName("USER")).thenReturn(Optional.empty());

        assertThrows(RegistrationFailedException.class, () -> registrationService.register(request));
        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UC18 BE6: unexpected persistence details are wrapped by a safe failure")
    void wrapsUnexpectedPersistenceFailure() {
        RegistrationRequest request = new RegistrationRequest("person@example.com", "Password1!");
        when(userAccountRepository.existsByEmail("person@example.com"))
            .thenThrow(new DataAccessResourceFailureException("internal-host:3306 password=secret"));

        RegistrationFailedException exception = assertThrows(
            RegistrationFailedException.class,
            () -> registrationService.register(request)
        );

        assertEquals("Registration could not be completed.", exception.getMessage());
        assertFalse(exception.getMessage().contains("internal-host"));
        assertFalse(exception.getMessage().contains("secret"));
    }
}
