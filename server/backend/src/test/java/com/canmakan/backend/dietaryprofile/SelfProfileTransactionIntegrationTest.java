package com.canmakan.backend.dietaryprofile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.canmakan.backend.auth.dto.RegistrationRequest;
import com.canmakan.backend.auth.dto.RegistrationResponse;
import com.canmakan.backend.auth.service.AuthService;
import com.canmakan.backend.dietaryprofile.dto.CreateSelfProfileRequest;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.dietaryprofile.service.DietaryProfileService;
import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Account registration and authenticated SELF-profile setup are deliberately separate
 * transactions. A profile failure must never remove the already committed account.
 */
@SpringBootTest
class SelfProfileTransactionIntegrationTest {

    private static final long MISSING_RESTRICTION_ID = Long.MAX_VALUE - 100;

    @Autowired
    private AuthService authService;

    @Autowired
    private DietaryProfileService dietaryProfileService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private DietaryProfileRepository dietaryProfileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private DietaryRestrictionRepository dietaryRestrictionRepository;

    private String createdEmail;

    @AfterEach
    void cleanUpAccount() {
        if (createdEmail != null) {
            jdbcTemplate.update("delete from users where email = ?", createdEmail);
        }
    }

    @Test
    void profileCreationFailureLeavesCommittedAccountAndNoEmptyProfile() {
        createdEmail = "profile-rollback." + UUID.randomUUID() + "@example.com";
        RegistrationResponse registration = authService.register(
            new RegistrationRequest("Person Name", createdEmail, "Password1!", null)
        );
        UserAccount before = userAccountRepository.findById(registration.userId()).orElseThrow();
        String passwordHashBefore = before.getPasswordHash();
        when(dietaryRestrictionRepository.findById(MISSING_RESTRICTION_ID))
            .thenReturn(Optional.empty());

        assertThrows(
            IllegalArgumentException.class,
            () -> dietaryProfileService.createSelfProfile(
                registration.userId(),
                new CreateSelfProfileRequest(
                    "Person Name",
                    Map.of(MISSING_RESTRICTION_ID, "STRICT_AVOID")
                )
            )
        );

        UserAccount after = userAccountRepository.findById(registration.userId()).orElseThrow();
        assertTrue(after.getEmail().equals(createdEmail));
        assertTrue(after.getPasswordHash().equals(passwordHashBefore));
        assertTrue(after.isActive() == before.isActive());
        assertTrue(dietaryProfileRepository.findByLinkedUser_Id(registration.userId()).isEmpty());
    }
}
