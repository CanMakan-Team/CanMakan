package com.canmakan.backend.dietaryprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestrictionId;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.dietaryprofile.repository.ProfileRestrictionRepository;
import com.canmakan.backend.dietaryprofile.service.DietaryProfileService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository-layer test for the "Edit Dietary Restrictions" feature (the profile
 * drawer's Edit Dietary Restrictions button, and toggling options in
 * DietaryRestrictionSheet), which is served by DietaryProfileController /
 * DietaryProfileService on top of {@link DietaryProfileRepository},
 * {@link DietaryRestrictionRepository}, and {@link ProfileRestrictionRepository}.
 *
 * <p>Fixtures are the seeded rows from {@code 05_household_dietary_data.sql}
 * rather than synthetic data, so these tests exercise the repository against
 * the same dataset the app actually ships with. {@code spring.sql.init.mode}
 * reloads that file on every context start and each test below runs inside a
 * transaction that is rolled back afterwards, so mutation tests are free to
 * edit seeded rows without one test's changes leaking into the next. The
 * trade-off: if the seed data in that file changes, the fixture ids/values
 * referenced here need to be updated to match.
 *
 * <p>Boots the full application context and runs against the app's real
 * configured MySQL datasource (same constraint as {@code BackendApplicationTests}):
 * the project has no embedded-database test dependency, and Spring Boot 4.1's
 * spring-boot-test-autoconfigure no longer ships the {@code @DataJpaTest} slice
 * ({@code @DataJpaTest}/{@code TestEntityManager}/{@code @AutoConfigureTestDatabase}
 * are absent from that artifact in this project's Spring Boot version), so a full
 * {@code @SpringBootTest} is used instead.
 */
@SpringBootTest
@Transactional
@DisplayName("Edit Dietary Restrictions: dietary profile repositories")
class DietaryProfileRepositoryTest {

    // Reference data seeded by 05_household_dietary_data.sql.
    private static final long RESTRICTION_GLUTEN = 1L;
    private static final long RESTRICTION_LOW_SUGAR = 11L;
    private static final long RESTRICTION_HALAL = 8L;
    private static final long RESTRICTION_VEGETARIAN = 9L;
    private static final long RESTRICTION_COUNT = 16;

    // Household profiles seeded by the same file.
    private static final long PROFILE_SARAH_TAN = 1L;
    private static final long PROFILE_MICHAEL_TAN = 2L;
    private static final long PROFILE_DANIEL_LIM = 6L;

    private static final long NONEXISTENT_ID = 9_999L;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private DietaryProfileRepository dietaryProfileRepository;

    @Autowired
    private DietaryRestrictionRepository dietaryRestrictionRepository;

    @Autowired
    private ProfileRestrictionRepository profileRestrictionRepository;

    @Autowired
    private DietaryProfileService dietaryProfileService;

    @Test
    @DisplayName("findAllRestrictions returns the full seeded catalog ordered by display name")
    void findAllRestrictionsReturnsSeededCatalogOrderedByDisplayName() {
        List<DietaryRestriction> all = dietaryRestrictionRepository.findAllOrderedByDisplayName();

        assertThat(all).hasSize((int) RESTRICTION_COUNT);
        // "Dairy Free" (2) < "Egg Allergy" (7) < "Fish Allergy" (5) < "Gluten Free" (1)
        // < "Halal" (8) < "Kosher" (15) < "Lactose Intolerant" (16) < "Low Fat" (12)
        // < "Low Salt" (14) < "Low Sugar" (11) < "Low Trans Fat" (13) < "Peanut Allergy" (3)
        // < "Shellfish Allergy" (4) < "Soy Allergy" (6) < "Vegan" (10) < "Vegetarian" (9)
        assertThat(all.stream().map(restriction -> restriction.getId()).toList())
            .containsExactly(2L, 7L, 5L, 1L, 8L, 15L, 16L, 12L, 14L, 11L, 13L, 3L, 4L, 6L, 10L, 9L);
    }

    @Test
    @DisplayName("findRestrictionById returns the seeded Halal restriction")
    void findRestrictionByIdReturnsSeededMatch() {
        Optional<DietaryRestriction> found = dietaryRestrictionRepository.findById(RESTRICTION_HALAL);

        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("HALAL");
        assertThat(found.get().getDisplayName()).isEqualTo("Halal");
        assertThat(found.get().getCategory()).isEqualTo("RELIGIOUS");
    }

    @Test
    @DisplayName("findRestrictionById returns empty for an id outside the seeded catalog")
    void findRestrictionByIdReturnsEmptyWhenMissing() {
        Optional<DietaryRestriction> found = dietaryRestrictionRepository.findById(NONEXISTENT_ID);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findProfileRestrictionsByProfileId returns Sarah Tan's seeded Gluten and Low Sugar selections")
    void findProfileRestrictionsByProfileIdReturnsSarahTansSelections() {
        List<ProfileRestriction> found =
            profileRestrictionRepository.findByDietaryProfileId(PROFILE_SARAH_TAN);

        assertThat(found)
            .extracting(
                pr -> pr.getDietaryRestriction().getDisplayName(),
                pr -> pr.getSeverityLevel())
            .containsExactlyInAnyOrder(
                tuple("Gluten Free", "STRICT_AVOID"),
                tuple("Low Sugar", "PREFERENCE")
            );
    }

    @Test
    @DisplayName("findProfileRestrictionsByProfileId returns Michael Tan's seeded Low Fat and Low Salt selections")
    void findProfileRestrictionsByProfileIdReturnsMichaelTansSelections() {
        List<ProfileRestriction> found =
            profileRestrictionRepository.findByDietaryProfileId(PROFILE_MICHAEL_TAN);

        assertThat(found)
            .extracting(
                pr -> pr.getDietaryRestriction().getDisplayName(),
                pr -> pr.getSeverityLevel())
            .containsExactlyInAnyOrder(
                tuple("Low Fat", "PREFERENCE"),
                tuple("Low Salt", "PREFERENCE")
            );
    }

    @Test
    @DisplayName("findProfileRestrictionsByProfileId returns an empty list for a profile id that does not exist")
    void findProfileRestrictionsByProfileIdReturnsEmptyListWhenProfileMissing() {
        List<ProfileRestriction> found =
            profileRestrictionRepository.findByDietaryProfileId(NONEXISTENT_ID);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("toggling on a new restriction for Daniel Lim adds it alongside his existing selections")
    void togglingOnNewRestrictionAddsItAlongsideExistingSelections() {
        // Daniel Lim (profile 6) is seeded with Shellfish + Halal, both STRICT_AVOID.
        DietaryProfile profile = dietaryProfileRepository.findById(PROFILE_DANIEL_LIM).orElseThrow();
        DietaryRestriction vegetarian = dietaryRestrictionRepository.findById(RESTRICTION_VEGETARIAN)
            .orElseThrow();

        ProfileRestriction newSelection = new ProfileRestriction();
        newSelection.setId(new ProfileRestrictionId(profile.getId(), vegetarian.getId()));
        newSelection.setDietaryProfile(profile);
        newSelection.setDietaryRestriction(vegetarian);
        newSelection.setSeverityLevel("STRICT_AVOID");
        profile.getProfileRestrictions().add(newSelection);

        dietaryProfileRepository.saveAndFlush(profile);
        entityManager.clear();

        List<ProfileRestriction> found =
            profileRestrictionRepository.findByDietaryProfileId(PROFILE_DANIEL_LIM);
        assertThat(found)
            .extracting(pr -> pr.getDietaryRestriction().getCode())
            .containsExactlyInAnyOrder("SHELLFISH", "HALAL", "VEGETARIAN");
    }

    @Test
    @DisplayName("toggling off Halal for Daniel Lim deletes only that row (orphanRemoval), keeping Shellfish")
    void togglingOffHalalDeletesOnlyThatRowViaOrphanRemoval() {
        // Daniel Lim (profile 6) is seeded with Shellfish + Halal, both STRICT_AVOID.
        DietaryProfile profile = dietaryProfileRepository.findById(PROFILE_DANIEL_LIM).orElseThrow();

        // Mutate the managed collection exactly as
        // DietaryProfileService.saveDietaryRestrictionSelections does: remove the
        // deselected restriction and save, relying on orphanRemoval to delete the row.
        profile.getProfileRestrictions().removeIf(
            pr -> pr.getDietaryRestriction().getId().equals(RESTRICTION_HALAL)
        );
        dietaryProfileRepository.saveAndFlush(profile);
        entityManager.clear();

        List<ProfileRestriction> remaining =
            profileRestrictionRepository.findByDietaryProfileId(PROFILE_DANIEL_LIM);
        assertThat(remaining)
            .extracting(pr -> pr.getDietaryRestriction().getCode())
            .containsExactly("SHELLFISH");
    }

    @Test
    @DisplayName("UC1-AC4: changing severity for an existing profile restriction updates it in place, not a duplicate")
    void changingSeverityForExistingRestrictionUpdatesInPlace() {
        // Sarah Tan (profile 1) is seeded with Gluten Free STRICT_AVOID and Low
        // Sugar PREFERENCE. Re-submitting the same restriction ids with Gluten's
        // severity changed mirrors what PUT /profiles/{id}/restrictions does when
        // a user changes severity in the Sheet rather than adding/removing.
        dietaryProfileService.saveDietaryRestrictionSelections(
            PROFILE_SARAH_TAN,
            Map.of(RESTRICTION_GLUTEN, "INTOLERANCE", RESTRICTION_LOW_SUGAR, "PREFERENCE")
        );
        // Flush before clearing: the service uses save(), not saveAndFlush(), which
        // is correct in production (Spring commits — and Hibernate flushes — when
        // the @Transactional HTTP request method returns). Here that save() call
        // joins this test's own outer transaction instead of committing on its own,
        // so an explicit flush is needed before clear() or the pending change would
        // be silently discarded rather than sent to the database.
        entityManager.flush();
        entityManager.clear();

        List<ProfileRestriction> found =
            profileRestrictionRepository.findByDietaryProfileId(PROFILE_SARAH_TAN);
        assertThat(found).hasSize(2);
        assertThat(found)
            .extracting(
                pr -> pr.getDietaryRestriction().getId(),
                pr -> pr.getSeverityLevel())
            .containsExactlyInAnyOrder(
                tuple(RESTRICTION_GLUTEN, "INTOLERANCE"),
                tuple(RESTRICTION_LOW_SUGAR, "PREFERENCE")
            );
    }

    @Test
    @DisplayName("UC1-AC6: PUT (save) persists changes and a subsequent GET returns exactly the saved set")
    void saveThenGetReturnsExactlyTheSavedSet() {
        // Exercises DietaryProfileService.saveDietaryRestrictionSelections and
        // getDietaryRestrictionsForProfile directly — the methods backing PUT and
        // GET /profiles/{id}/restrictions respectively — rather than the raw
        // repository, since this criterion describes that round trip specifically.
        // Daniel Lim (profile 6) starts with Shellfish + Halal; replacing the set
        // with only Vegetarian exercises add + remove together in one save.
        dietaryProfileService.saveDietaryRestrictionSelections(
            PROFILE_DANIEL_LIM,
            Map.of(RESTRICTION_VEGETARIAN, "STRICT_AVOID")
        );
        // See the flush comment in changingSeverityForExistingRestrictionUpdatesInPlace.
        entityManager.flush();
        entityManager.clear();

        Map<Long, String> saved = dietaryProfileService.getDietaryRestrictionsForProfile(PROFILE_DANIEL_LIM);

        assertThat(saved).containsExactlyEntriesOf(Map.of(RESTRICTION_VEGETARIAN, "STRICT_AVOID"));
    }
}
