package com.canmakan.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.auth.dto.RegistrationRequest;
import com.canmakan.backend.auth.dto.RegistrationResponse;
import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
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
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Set<String> createdEmails = new HashSet<>();

    @AfterEach
    void cleanUpAccounts() {
        // Raw SQL avoids a Hibernate persistence-context conflict from deleting a UserAccount
        // that now always has a linked dietary_profiles row; the FK is ON DELETE CASCADE.
        for (String email : createdEmails) {
            jdbcTemplate.update("delete from users where email = ?", email);
        }
    }

    @Test
    void registrationPersistsUserAndLinkedSelfProfileTogether() {
        String email = uniqueEmail("account-and-profile");

        RegistrationResponse response = authService.register(
            new RegistrationRequest("Person Name", email, "Password1!", null)
        );

        UserAccount account = userAccountRepository.findByEmail(email).orElseThrow();
        assertEquals(account.getId(), response.userId());
        assertEquals(email, response.email());
        assertTrue(account.isActive());
        assertEquals("USER", userAccountRepository.findRoleNameById(account.getRoleId()).orElseThrow());

        DietaryProfile profile = dietaryProfileRepository.findByLinkedUser_Id(account.getId())
            .orElseThrow(() -> new AssertionError("Registration must create the linked SELF profile."));
        assertEquals("Person Name", profile.getProfileName());
        assertEquals("SELF", profile.getRelationship());
        assertTrue(profile.isPrimary());
    }

    @Test
    void laterInvitationFailureCannotRollBackTheCommittedAccountOrProfile() {
        String email = uniqueEmail("failed-claim");
        RegistrationResponse response = authService.register(
            new RegistrationRequest("Person Name", email, "Password1!", "legacy-token")
        );

        assertThrows(
            InvitationNotFoundException.class,
            () -> familyService.acceptInvitation(response.userId(), "missing-" + UUID.randomUUID())
        );

        assertTrue(userAccountRepository.findByEmail(email).isPresent());
        assertTrue(dietaryProfileRepository.findByLinkedUser_Id(response.userId()).isPresent());
    }

    private String uniqueEmail(String prefix) {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        return email;
    }
}
