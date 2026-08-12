package com.canmakan.backend.dietaryprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.canmakan.backend.auth.AuthService;
import com.canmakan.backend.auth.dto.RegistrationRequest;
import com.canmakan.backend.auth.dto.RegistrationResponse;
import com.canmakan.backend.dietaryprofile.dto.CreateSelfProfileRequest;
import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.dietaryprofile.service.DietaryProfileService;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
            userAccountRepository.findByEmail(createdEmail).ifPresent(userAccountRepository::delete);
            userAccountRepository.flush();
        }
    }

    @Test
    void childPersistenceFailureRollsBackProfileButLeavesAccountUnchanged() {
        createdEmail = "profile-rollback." + UUID.randomUUID() + "@example.com";
        RegistrationResponse registration = authService.register(
            new RegistrationRequest(null, createdEmail, "Password1!", null)
        );
        UserAccount before = userAccountRepository.findById(registration.userId()).orElseThrow();
        String passwordHashBefore = before.getPasswordHash();

        DietaryRestriction missingRestriction = new DietaryRestriction();
        missingRestriction.setId(MISSING_RESTRICTION_ID);
        when(dietaryRestrictionRepository.findById(MISSING_RESTRICTION_ID))
            .thenReturn(Optional.of(missingRestriction));

        assertThrows(
            ObjectRetrievalFailureException.class,
            () -> dietaryProfileService.createSelfProfile(
                registration.userId(),
                new CreateSelfProfileRequest(
                    "Person Name",
                    Map.of(MISSING_RESTRICTION_ID, "STRICT_AVOID")
                )
            )
        );

        UserAccount after = userAccountRepository.findById(registration.userId()).orElseThrow();
        assertEquals(createdEmail, after.getEmail());
        assertEquals(passwordHashBefore, after.getPasswordHash());
        assertEquals(before.isActive(), after.isActive());
        assertFalse(dietaryProfileRepository.findByLinkedUser_Id(registration.userId()).isPresent());
        assertEquals(
            0L,
            jdbcTemplate.queryForObject(
                "select count(*) from profile_restrictions where dietary_profile_id in "
                    + "(select id from dietary_profiles where linked_user_id = ?)",
                Long.class,
                registration.userId()
            )
        );
    }
}
