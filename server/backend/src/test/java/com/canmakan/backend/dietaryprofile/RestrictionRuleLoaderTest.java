package com.canmakan.backend.dietaryprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestriction;
import com.canmakan.backend.dietaryprofile.repository.ProfileRestrictionRepository;
import com.canmakan.backend.dietaryprofile.service.RestrictionRuleLoader;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RestrictionRuleLoader}.
 */
@ExtendWith(MockitoExtension.class)
class RestrictionRuleLoaderTest {

    @Mock
    private ProfileRestrictionRepository repository;

    @InjectMocks
    private RestrictionRuleLoader loader;

    @Test
    void mapsProfileRestrictionsToRules() {
        when(repository.findByDietaryProfileId(1L)).thenReturn(List.of(
                profileRestriction("PEANUT", "ALLERGEN", "STRICT_AVOID"),
                profileRestriction("DAIRY", "ALLERGEN", "INTOLERANCE")));

        List<RestrictionRule> rules = loader.load(1L);

        assertEquals(2, rules.size());
        assertEquals("PEANUT", rules.get(0).code());
        assertEquals(RestrictionCategory.ALLERGEN, rules.get(0).category());
        assertEquals(RestrictionSeverity.STRICT_AVOID, rules.get(0).severity());
        assertEquals(RestrictionSeverity.INTOLERANCE, rules.get(1).severity());
    }

    @Test
    void mappingIsCaseInsensitiveAndTrimmed() {
        when(repository.findByDietaryProfileId(1L)).thenReturn(List.of(
                profileRestriction("HALAL", " religious ", " strict_avoid ")));

        List<RestrictionRule> rules = loader.load(1L);

        assertEquals(1, rules.size());
        assertEquals(RestrictionCategory.RELIGIOUS, rules.get(0).category());
        assertEquals(RestrictionSeverity.STRICT_AVOID, rules.get(0).severity());
    }

    @Test
    void skipsRowsWithUnknownCategoryOrSeverityInsteadOfFailing() {
        when(repository.findByDietaryProfileId(1L)).thenReturn(List.of(
                profileRestriction("X", "NONSENSE", "STRICT_AVOID"),
                profileRestriction("Y", "ALLERGEN", "MAYBE"),
                profileRestriction("HALAL", "RELIGIOUS", "STRICT_AVOID")));

        List<RestrictionRule> rules = loader.load(1L);

        assertEquals(1, rules.size());
        assertEquals("HALAL", rules.get(0).code());
    }

    @Test
    void returnsEmptyListWhenProfileHasNoRestrictions() {
        when(repository.findByDietaryProfileId(1L)).thenReturn(List.of());

        assertTrue(loader.load(1L).isEmpty());
    }

    @Test
    void throwsWhenProfileIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> loader.load(null));
        verifyNoInteractions(repository);
    }

    private static ProfileRestriction profileRestriction(String code, String category, String severity) {
        DietaryRestriction restriction = new DietaryRestriction();
        restriction.setCode(code);
        restriction.setCategory(category);

        ProfileRestriction profileRestriction = new ProfileRestriction();
        profileRestriction.setDietaryRestriction(restriction);
        profileRestriction.setSeverityLevel(severity);
        return profileRestriction;
    }
}
