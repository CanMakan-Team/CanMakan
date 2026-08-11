package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeResponse

@DisplayName("D3: RestrictionEditAuthorization")
class RestrictionEditAuthorizationTest {

    @Test
    @DisplayName("Allows edit when the user has no family (personal profile)")
    fun allowsWhenNoFamily() {
        assertTrue(
            RestrictionEditAuthorization.mayEditRestrictions(
                profileId = 1L,
                hasFamily = false,
                me = null,
            )
        )
    }

    @Test
    @DisplayName("Allows edit for the actor's self-linked profile")
    fun allowsSelfProfile() {
        assertTrue(
            RestrictionEditAuthorization.mayEditRestrictions(
                profileId = SELF_PROFILE_ID,
                hasFamily = true,
                me = FAMILY_ME,
            )
        )
    }

    @Test
    @DisplayName("Allows PRIMARY_ADMIN to edit a dependant profile")
    fun allowsAdminDependant() {
        assertTrue(
            RestrictionEditAuthorization.mayEditRestrictions(
                profileId = DEPENDANT_PROFILE_ID,
                hasFamily = true,
                me = FAMILY_ME,
            )
        )
    }

    @Test
    @DisplayName("Allows PRIMARY_ADMIN to edit another adult's linked profile")
    fun allowsAdminOtherAdult() {
        assertTrue(
            RestrictionEditAuthorization.mayEditRestrictions(
                profileId = OTHER_ADULT_PROFILE_ID,
                hasFamily = true,
                me = FAMILY_ME,
            )
        )
    }

    @Test
    @DisplayName("Denies a non-admin editing a dependant")
    fun deniesNonAdminDependant() {
        assertFalse(
            RestrictionEditAuthorization.mayEditRestrictions(
                profileId = DEPENDANT_PROFILE_ID,
                hasFamily = true,
                me = FAMILY_ME.copy(memberRole = "MEMBER"),
            )
        )
    }

    @Test
    @DisplayName("Denies a non-admin editing another adult's linked profile")
    fun deniesNonAdminOtherAdult() {
        assertFalse(
            RestrictionEditAuthorization.mayEditRestrictions(
                profileId = OTHER_ADULT_PROFILE_ID,
                hasFamily = true,
                me = FAMILY_ME.copy(memberRole = "MEMBER"),
            )
        )
    }

    private companion object {
        const val SELF_PROFILE_ID = 77L
        const val DEPENDANT_PROFILE_ID = 88L
        const val OTHER_ADULT_PROFILE_ID = 99L

        val FAMILY_ME = FamilyMeResponse(
            familyId = 50L,
            familyName = "Wong Family",
            memberRole = "PRIMARY_ADMIN",
            selfProfileId = SELF_PROFILE_ID,
            createdByUserId = 14L,
        )
    }
}
