package com.canmakan.backend.dietaryprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.dietaryprofile.dto.SelfProfileResponse;
import com.canmakan.backend.dietaryprofile.service.DietaryProfileService;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Regression coverage for the Dietary Profile page failing to load an existing
 * profile: {@code GET /api/profiles/me} was surfacing a generic 500 for a
 * returning user because {@code DietaryProfileService.getSelfProfile} read the
 * lazy {@code profileRestrictions} collection without a transaction. Since
 * {@code spring.jpa.open-in-view} is disabled, the Hibernate session used by
 * the repository lookup closes as soon as that call returns, so accessing the
 * collection afterwards throws {@code LazyInitializationException}.
 *
 * <p>Deliberately has no class- or method-level {@code @Transactional}: a test
 * transaction would keep the Hibernate session open for the whole test and
 * mask exactly the bug this guards against. Only reads seeded data, so no
 * rollback is needed.
 */
@SpringBootTest
class GetSelfProfileIntegrationTest {

    @Autowired
    private DietaryProfileService dietaryProfileService;

    @Test
    @DisplayName(
        "getSelfProfile loads an existing profile's restrictions outside any "
            + "caller transaction without throwing LazyInitializationException")
    void getSelfProfileReadsLazyRestrictionsWithoutAnEnclosingTransaction() {
        // Sarah Tan (dietary_profiles id 1, linked_user_id 4) is seeded by
        // 05_household_dietary_data.sql with Gluten Intolerance (STRICT_AVOID)
        // and Low Sugar (PREFERENCE).
        SelfProfileResponse response = dietaryProfileService.getSelfProfile(4L);

        assertThat(response.profileId()).isEqualTo(1L);
        assertThat(response.profileName()).isEqualTo("Sarah Tan");
        assertThat(response.relationship()).isEqualTo("SELF");
        assertThat(response.active()).isTrue();
        assertThat(response.restrictions())
            .isEqualTo(Map.of(1L, "STRICT_AVOID", 11L, "PREFERENCE"));
    }
}
