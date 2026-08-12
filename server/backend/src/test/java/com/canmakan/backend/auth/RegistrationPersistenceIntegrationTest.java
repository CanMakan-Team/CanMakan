package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.auth.dto.RegistrationRequest;
import com.canmakan.backend.auth.dto.RegistrationResponse;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.family.FamilyService;
import com.canmakan.backend.family.exception.InvitationNotFoundException;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RegistrationPersistenceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private FamilyService familyService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private DietaryProfileRepository dietaryProfileRepository;

    private final Set<String> createdEmails = new HashSet<>();

    @AfterEach
    void cleanUpAccounts() {
        for (String email : createdEmails) {
            userAccountRepository.findByEmail(email).ifPresent(userAccountRepository::delete);
        }
        userAccountRepository.flush();
    }

    @Test
    void accountOnlyRegistrationPersistsUserWithoutDietaryProfile() {
        String email = uniqueEmail("account-only");

        RegistrationResponse response = authService.register(
            new RegistrationRequest(null, email, "Password1!", null)
        );

        UserAccount account = userAccountRepository.findByEmail(email).orElseThrow();
        assertEquals(account.getId(), response.userId());
        assertEquals(email, response.email());
        assertTrue(account.isActive());
        assertEquals("USER", userAccountRepository.findRoleNameById(account.getRoleId()).orElseThrow());
        assertFalse(dietaryProfileRepository.findByLinkedUser_Id(account.getId()).isPresent());
    }

    @Test
    void laterInvitationFailureCannotRollBackCommittedAccount() {
        String email = uniqueEmail("failed-claim");
        RegistrationResponse response = authService.register(
            new RegistrationRequest(null, email, "Password1!", "legacy-token")
        );

        assertThrows(
            InvitationNotFoundException.class,
            () -> familyService.acceptInvitation(response.userId(), "missing-" + UUID.randomUUID())
        );

        assertTrue(userAccountRepository.findByEmail(email).isPresent());
        assertFalse(dietaryProfileRepository.findByLinkedUser_Id(response.userId()).isPresent());
    }

    private String uniqueEmail(String prefix) {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        return email;
    }
}
