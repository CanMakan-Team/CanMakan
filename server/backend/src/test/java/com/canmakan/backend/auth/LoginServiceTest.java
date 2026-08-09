package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.DietaryProfile;
import com.canmakan.backend.dietaryprofile.DietaryProfileRepository;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Login service tests
 * 
 * @author Amelia
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("UC19: LoginService")
class LoginServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private DietaryProfileRepository dietaryProfileRepository;

    private PasswordEncoder passwordEncoder;
    private LoginService loginService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(10);
        loginService = new LoginService(
            userAccountRepository,
            dietaryProfileRepository,
            passwordEncoder
        );
    }

    @Test
    @DisplayName("returns session fields for an active USER with matching password")
    void loginSucceedsForActiveUser() {
        UserAccount account = activeUser(14L, "person@example.com", "Password1!", 2L);
        when(userAccountRepository.findByEmail("person@example.com")).thenReturn(Optional.of(account));
        when(userAccountRepository.findRoleNameById(2L)).thenReturn(Optional.of("USER"));
        DietaryProfile profile = new DietaryProfile();
        profile.setProfileName("Person Name");
        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.of(profile));

        LoginResponse response = loginService.login(
            new LoginRequest("  Person@Example.COM  ", "Password1!")
        );

        assertEquals(14L, response.userId());
        assertEquals("Person Name", response.displayName());
        assertEquals(List.of("ROLE_APP_USER", "ROLE_FAMILY_ADMIN"), response.roles());
        assertFalse(response.prototype());
    }

    @Test
    @DisplayName("maps ADMIN platform role to ROLE_SYSTEM_ADMIN")
    void loginMapsAdminRole() {
        UserAccount account = activeUser(1L, "admin@example.com", "Password1!", 1L);
        when(userAccountRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(account));
        when(userAccountRepository.findRoleNameById(1L)).thenReturn(Optional.of("ADMIN"));
        when(dietaryProfileRepository.findByLinkedUser_Id(1L)).thenReturn(Optional.empty());

        LoginResponse response = loginService.login(
            new LoginRequest("admin@example.com", "Password1!")
        );

        assertEquals(List.of("ROLE_SYSTEM_ADMIN"), response.roles());
        assertEquals("admin", response.displayName());
    }

    @Test
    @DisplayName("rejects unknown email with InvalidCredentialsException")
    void unknownEmailRejected() {
        when(userAccountRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(
            InvalidCredentialsException.class,
            () -> loginService.login(new LoginRequest("missing@example.com", "Password1!"))
        );
        verifyNoInteractions(dietaryProfileRepository);
    }

    @Test
    @DisplayName("rejects wrong password with InvalidCredentialsException")
    void wrongPasswordRejected() {
        UserAccount account = activeUser(14L, "person@example.com", "Password1!", 2L);
        when(userAccountRepository.findByEmail("person@example.com")).thenReturn(Optional.of(account));

        assertThrows(
            InvalidCredentialsException.class,
            () -> loginService.login(new LoginRequest("person@example.com", "WrongPassword1!"))
        );
    }

    @Test
    @DisplayName("rejects inactive account with InvalidCredentialsException")
    void inactiveAccountRejected() {
        UserAccount account = activeUser(14L, "person@example.com", "Password1!", 2L);
        account.setActive(false);
        when(userAccountRepository.findByEmail("person@example.com")).thenReturn(Optional.of(account));

        assertThrows(
            InvalidCredentialsException.class,
            () -> loginService.login(new LoginRequest("person@example.com", "Password1!"))
        );
        verify(userAccountRepository).findByEmail("person@example.com");
    }

    private UserAccount activeUser(long id, String email, String rawPassword, long roleId) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setEmail(email);
        account.setRoleId(roleId);
        account.setActive(true);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        return account;
    }
}
