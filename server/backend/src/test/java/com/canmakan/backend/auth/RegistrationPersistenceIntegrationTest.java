package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.auth.dto.RegistrationRequest;
import com.canmakan.backend.auth.dto.RegistrationResponse;
import com.canmakan.backend.auth.service.AuthService;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class RegistrationPersistenceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private DietaryProfileRepository dietaryProfileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Set<String> createdEmails = new HashSet<>();

    @AfterEach
    void cleanUpAccounts() {
        for (String email : createdEmails) {
            jdbcTemplate.update("delete from users where email = ?", email);
        }
    }

    @Test
    void registrationPersistsOnlyAnActiveUserAccount() {
        String email = uniqueEmail("account-only");

        RegistrationResponse response = authService.register(
            new RegistrationRequest("Person Name", email, "Password1!", null)
        );

        UserAccount account = userAccountRepository.findByEmail(email).orElseThrow();
        assertEquals(account.getId(), response.userId());
        assertEquals(email, response.email());
        assertTrue(account.isActive());
        assertEquals("USER", userAccountRepository.findRoleNameById(account.getRoleId()).orElseThrow());

        assertTrue(dietaryProfileRepository.findByLinkedUser_Id(account.getId()).isEmpty());
    }

    @Test
    void legacyNameAndInvitationTokenHaveNoProfileOrSessionSideEffect() {
        String email = uniqueEmail("legacy-fields");
        RegistrationResponse response = authService.register(
            new RegistrationRequest("Person Name", email, "Password1!", "legacy-token")
        );

        assertTrue(userAccountRepository.findByEmail(email).isPresent());
        assertTrue(dietaryProfileRepository.findByLinkedUser_Id(response.userId()).isEmpty());
    }

    private String uniqueEmail(String prefix) {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        return email;
    }
}
