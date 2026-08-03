package com.canmakan.backend.dietaryprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
* Backend testing for Use Case 1: Update App User Dietary Profile
*
* @author Amelia
*/

@ExtendWith(MockitoExtension.class)
class DietaryProfileServiceTest {

    @Mock
    private DietaryProfileRepository dietaryProfileRepository;

    @InjectMocks
    private DietaryProfileService dietaryProfileService;

    @Test
    @DisplayName("UC1 BE1: Saves a selected dietary restriction for a profile")
    void saveDietaryRestrictionSelectionsReplacesSelectionsInTheProfileCollection() {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(1L);

        DietaryRestriction restriction = new DietaryRestriction();
        restriction.setId(2L);

        ProfileRestriction existingRestriction = new ProfileRestriction();
        existingRestriction.setId(new ProfileRestrictionId(1L, 3L));
        existingRestriction.setDietaryProfile(profile);
        existingRestriction.setDietaryRestriction(new DietaryRestriction());
        existingRestriction.getDietaryRestriction().setId(3L);
        existingRestriction.setSeverityLevel("LOW_RISK");
        Set<ProfileRestriction> initialRestrictions = new HashSet<>();
        initialRestrictions.add(existingRestriction);
        profile.setProfileRestrictions(initialRestrictions);

        when(dietaryProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(dietaryProfileRepository.findRestrictionById(2L)).thenReturn(Optional.of(restriction));

        dietaryProfileService.saveDietaryRestrictionSelections(1L, Map.of(2L, "STRICT_AVOID"));

        assertEquals(1, profile.getProfileRestrictions().size());
        ProfileRestriction savedRestriction = profile.getProfileRestrictions().iterator().next();
        assertEquals(2L, savedRestriction.getDietaryRestriction().getId());
        assertEquals("STRICT_AVOID", savedRestriction.getSeverityLevel());
        verify(dietaryProfileRepository).save(profile);
    }

    @Test
    @DisplayName("UC1 BE2: Removes deselected dietary restrictions when saving")
    void saveDietaryRestrictionSelectionsRemovesDeselectedRestrictions() {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(1L);

        ProfileRestriction keptRestriction = new ProfileRestriction();
        keptRestriction.setId(new ProfileRestrictionId(1L, 2L));
        keptRestriction.setDietaryProfile(profile);
        keptRestriction.setDietaryRestriction(createRestriction(2L));
        keptRestriction.setSeverityLevel("LOW_RISK");

        ProfileRestriction removedRestriction = new ProfileRestriction();
        removedRestriction.setId(new ProfileRestrictionId(1L, 3L));
        removedRestriction.setDietaryProfile(profile);
        removedRestriction.setDietaryRestriction(createRestriction(3L));
        removedRestriction.setSeverityLevel("STRICT_AVOID");

        profile.setProfileRestrictions(new HashSet<>(Set.of(keptRestriction, removedRestriction)));

        when(dietaryProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(dietaryProfileRepository.findRestrictionById(2L)).thenReturn(Optional.of(createRestriction(2L)));

        dietaryProfileService.saveDietaryRestrictionSelections(1L, Map.of(2L, "STRICT_AVOID"));

        assertEquals(1, profile.getProfileRestrictions().size());
        assertEquals(2L, profile.getProfileRestrictions().iterator().next().getDietaryRestriction().getId());
        verify(dietaryProfileRepository).save(profile);
    }

    @Test
    @DisplayName("UC1 BE3: Throws exception when the target profile does not exist")
    void saveDietaryRestrictionSelectionsThrowsWhenProfileDoesNotExist() {
        when(dietaryProfileRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> dietaryProfileService.saveDietaryRestrictionSelections(99L, Map.of(2L, "STRICT_AVOID"))
        );

        assertEquals("Profile not found: 99", exception.getMessage());
        verify(dietaryProfileRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("UC1 BE4: Throws exception when a requested restriction does not exist")
    void saveDietaryRestrictionSelectionsThrowsWhenRestrictionDoesNotExist() {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(1L);
        profile.setProfileRestrictions(new HashSet<>());

        when(dietaryProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(dietaryProfileRepository.findRestrictionById(42L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> dietaryProfileService.saveDietaryRestrictionSelections(1L, Map.of(42L, "STRICT_AVOID"))
        );

        assertEquals("Restriction not found: 42", exception.getMessage());
        verify(dietaryProfileRepository, never()).save(profile);
    }

    // Helpers

    private DietaryRestriction createRestriction(Long id) {
        DietaryRestriction restriction = new DietaryRestriction();
        restriction.setId(id);
        return restriction;
    }
}
