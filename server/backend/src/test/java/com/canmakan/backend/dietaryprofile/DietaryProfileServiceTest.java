package com.canmakan.backend.dietaryprofile;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DietaryProfileServiceTest {

    @Mock
    private DietaryProfileRepository dietaryProfileRepository;

    @InjectMocks
    private DietaryProfileService dietaryProfileService;

    @Test
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
}
