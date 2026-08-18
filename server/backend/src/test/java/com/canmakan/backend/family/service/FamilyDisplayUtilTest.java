package com.canmakan.backend.family.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestriction;
import com.canmakan.backend.family.dto.FamilyMeRestrictionDetail;
import com.canmakan.backend.family.service.FamilyDisplayUtil.RestrictionCodeSplit;
import com.canmakan.backend.user.model.UserAccount;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FamilyDisplayUtil")
class FamilyDisplayUtilTest {

    @Test
    @DisplayName("normalizeEmail returns null for a null email")
    void normalizeEmailReturnsNullForNull() {
        assertNull(FamilyDisplayUtil.normalizeEmail(null));
    }

    @Test
    @DisplayName("normalizeEmail strips whitespace and lowercases the value")
    void normalizeEmailStripsAndLowercases() {
        assertEquals("jamie@example.com", FamilyDisplayUtil.normalizeEmail("  Jamie@Example.COM  "));
    }

    @Test
    @DisplayName("maskEmail returns an empty string for a null or blank email")
    void maskEmailReturnsEmptyForNullOrBlank() {
        assertEquals("", FamilyDisplayUtil.maskEmail(null));
        assertEquals("", FamilyDisplayUtil.maskEmail("   "));
    }

    @Test
    @DisplayName("maskEmail returns a fixed mask when there is no usable '@' sign")
    void maskEmailReturnsFixedMaskWhenNoAtSign() {
        assertEquals("***", FamilyDisplayUtil.maskEmail("notanemail"));
        assertEquals("***", FamilyDisplayUtil.maskEmail("@example.com"));
    }

    @Test
    @DisplayName("maskEmail masks a short local part with a single visible character")
    void maskEmailMasksShortLocalPart() {
        assertEquals("a***@example.com", FamilyDisplayUtil.maskEmail("ab@example.com"));
    }

    @Test
    @DisplayName("maskEmail masks a longer local part keeping the first and last characters")
    void maskEmailMasksLongLocalPart() {
        assertEquals("j***e@example.com", FamilyDisplayUtil.maskEmail("jamie@example.com"));
    }

    @Test
    @DisplayName("isMembershipUniqueViolation detects a MySQL duplicate-key error code")
    void isMembershipUniqueViolationDetectsErrorCode() {
        SQLException sqlException = new SQLException("Duplicate entry", "23000", 1062);

        assertTrue(FamilyDisplayUtil.isMembershipUniqueViolation(sqlException));
    }

    @Test
    @DisplayName("isMembershipUniqueViolation detects the constraint name in the message text")
    void isMembershipUniqueViolationDetectsConstraintNameInMessage() {
        RuntimeException exception =
                new RuntimeException("Duplicate entry violates UQ_FAMILY_MEMBERS_USER_ID");

        assertTrue(FamilyDisplayUtil.isMembershipUniqueViolation(exception));
    }

    @Test
    @DisplayName("isMembershipUniqueViolation detects the column reference in the message text")
    void isMembershipUniqueViolationDetectsColumnReferenceInMessage() {
        RuntimeException exception =
                new RuntimeException("Column 'family_members.user_id' is not unique");

        assertTrue(FamilyDisplayUtil.isMembershipUniqueViolation(exception));
    }

    @Test
    @DisplayName("isMembershipUniqueViolation walks the cause chain to find a matching root cause")
    void isMembershipUniqueViolationWalksCauseChain() {
        SQLException root = new SQLException("Duplicate entry", "23000", 1062);
        RuntimeException wrapper = new RuntimeException("save failed", root);

        assertTrue(FamilyDisplayUtil.isMembershipUniqueViolation(wrapper));
    }

    @Test
    @DisplayName("isMembershipUniqueViolation returns false when nothing in the chain matches")
    void isMembershipUniqueViolationReturnsFalseWhenNoMatch() {
        RuntimeException exception = new RuntimeException("some other failure", new RuntimeException());

        assertFalse(FamilyDisplayUtil.isMembershipUniqueViolation(exception));
    }

    @Test
    @DisplayName("isMembershipUniqueViolation returns false for an exception with no message")
    void isMembershipUniqueViolationReturnsFalseForNullMessage() {
        assertFalse(FamilyDisplayUtil.isMembershipUniqueViolation(new RuntimeException()));
    }

    @Test
    @DisplayName("profileNameFromUser falls back to a default name when the email is null or blank")
    void profileNameFromUserFallsBackForNullOrBlankEmail() {
        UserAccount blankEmailUser = new UserAccount();
        blankEmailUser.setEmail("   ");

        assertEquals("My Profile", FamilyDisplayUtil.profileNameFromUser(new UserAccount()));
        assertEquals("My Profile", FamilyDisplayUtil.profileNameFromUser(blankEmailUser));
    }

    @Test
    @DisplayName("profileNameFromUser uses the local part of the email when present")
    void profileNameFromUserUsesLocalPart() {
        UserAccount user = new UserAccount();
        user.setEmail("jamie@example.com");

        assertEquals("jamie", FamilyDisplayUtil.profileNameFromUser(user));
    }

    @Test
    @DisplayName("profileNameFromUser uses the whole value when there is no '@' sign")
    void profileNameFromUserUsesWholeValueWhenNoAtSign() {
        UserAccount user = new UserAccount();
        user.setEmail("jamie");

        assertEquals("jamie", FamilyDisplayUtil.profileNameFromUser(user));
    }

    @Test
    @DisplayName("profileNameFromUser falls back to a default name when the local part is blank")
    void profileNameFromUserFallsBackWhenLocalPartIsBlank() {
        UserAccount user = new UserAccount();
        user.setEmail("   @example.com");

        assertEquals("My Profile", FamilyDisplayUtil.profileNameFromUser(user));
    }

    @Test
    @DisplayName("mapRestrictions returns an empty list when there is no profile")
    void mapRestrictionsReturnsEmptyListWhenProfileMissing() {
        assertEquals(List.of(), FamilyDisplayUtil.mapRestrictions(Optional.empty()));
    }

    @Test
    @DisplayName("mapRestrictions converts each profile restriction into a detail DTO")
    void mapRestrictionsConvertsRestrictions() {
        DietaryProfile profile = new DietaryProfile();
        profile.setProfileRestrictions(Set.of(restriction("GLUTEN", "Gluten", "OTHER", "SEVERE")));

        List<FamilyMeRestrictionDetail> details =
                FamilyDisplayUtil.mapRestrictions(Optional.of(profile));

        assertEquals(1, details.size());
        assertEquals("GLUTEN", details.get(0).code());
        assertEquals("Gluten", details.get(0).displayName());
        assertEquals("SEVERE", details.get(0).severity());
    }

    @Test
    @DisplayName("splitRestrictionCodes returns empty lists when there is no profile")
    void splitRestrictionCodesReturnsEmptyWhenProfileMissing() {
        RestrictionCodeSplit split = FamilyDisplayUtil.splitRestrictionCodes(Optional.empty());

        assertTrue(split.commonRequirements().isEmpty());
        assertTrue(split.restrictions().isEmpty());
    }

    @Test
    @DisplayName("splitRestrictionCodes routes religious codes to commonRequirements and others to restrictions")
    void splitRestrictionCodesRoutesByCategory() {
        DietaryProfile profile = new DietaryProfile();
        profile.setProfileRestrictions(Set.of(
                restriction("HALAL", "Halal", " religious ", "MODERATE"),
                restriction("GLUTEN", "Gluten", "MEDICAL", "SEVERE")
        ));

        RestrictionCodeSplit split = FamilyDisplayUtil.splitRestrictionCodes(Optional.of(profile));

        assertEquals(List.of("HALAL"), split.commonRequirements());
        assertEquals(List.of("GLUTEN"), split.restrictions());
    }

    @Test
    @DisplayName("splitRestrictionCodes skips rows missing a restriction reference or code, and treats a null category as non-religious")
    void splitRestrictionCodesSkipsIncompleteRowsAndDefaultsNullCategory() {
        DietaryProfile profile = new DietaryProfile();
        ProfileRestriction missingRestriction = new ProfileRestriction();
        missingRestriction.setDietaryRestriction(null);
        ProfileRestriction missingCode = new ProfileRestriction();
        missingCode.setDietaryRestriction(restrictionRef(null, "Unknown", null));
        ProfileRestriction nullCategory = new ProfileRestriction();
        nullCategory.setDietaryRestriction(restrictionRef("VEGAN", "Vegan", null));
        profile.setProfileRestrictions(Set.of(missingRestriction, missingCode, nullCategory));

        RestrictionCodeSplit split = FamilyDisplayUtil.splitRestrictionCodes(Optional.of(profile));

        assertTrue(split.commonRequirements().isEmpty());
        assertEquals(List.of("VEGAN"), split.restrictions());
    }

    private static ProfileRestriction restriction(
            String code, String displayName, String category, String severity) {
        ProfileRestriction profileRestriction = new ProfileRestriction();
        profileRestriction.setDietaryRestriction(restrictionRef(code, displayName, category));
        profileRestriction.setSeverityLevel(severity);
        return profileRestriction;
    }

    private static DietaryRestriction restrictionRef(String code, String displayName, String category) {
        DietaryRestriction restriction = new DietaryRestriction();
        restriction.setCode(code);
        restriction.setDisplayName(displayName);
        restriction.setCategory(category);
        return restriction;
    }
}
