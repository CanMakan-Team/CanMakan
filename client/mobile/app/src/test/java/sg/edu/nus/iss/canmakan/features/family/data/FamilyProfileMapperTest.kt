package sg.edu.nus.iss.canmakan.features.family.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FamilyProfileMapperTest {

    @Test
    @DisplayName("UC11 M1: Maps backend profile response to DietaryProfile domain model")
    fun mapsBackendProfileResponseToUiProfile() {
        val response = FamilyProfileResponse(
            id = 7L,
            profileName = "Noah",
            familyId = 1L,
            relationship = "Child",
            initials = "N",
            isPrimary = false
        )

        val profile = FamilyProfileMapper.fromResponse(response)

        assertEquals(7L, profile.id)
        assertEquals(1L, profile.familyId)
        assertEquals("Noah", profile.profileName)
        assertEquals("Child", profile.relationship)
        assertEquals("N", profile.initials)
        assertEquals(false, profile.isPrimary)
    }

    @Test
    fun mapsActiveProfileResponseInitialsFromTwoNames() {
        val profile = FamilyProfileMapper.fromActiveResponse(
            ActiveProfileResponse(
                profileId = 42L,
                profileName = "Wong Family Admin",
                relationship = "SELF",
                familyId = 7L,
                isPrimary = true,
            ),
        )
        assertEquals("WA", profile.initials)
        assertEquals(7L, profile.familyId)
        assertEquals(true, profile.isPrimary)
    }

    @Test
    fun mapsActiveProfileResponseSingleWordAndMissingOptionalFields() {
        val profile = FamilyProfileMapper.fromActiveResponse(
            ActiveProfileResponse(
                profileId = 1L,
                profileName = "  A  ",
                relationship = null,
                familyId = null,
                isPrimary = null,
            ),
        )
        assertEquals("A", profile.initials)
        assertEquals(0L, profile.familyId)
        assertEquals("", profile.relationship)
        assertEquals(false, profile.isPrimary)
    }

    @Test
    fun mapsEmptyActiveProfileNameToQuestionMarkInitials() {
        val profile = FamilyProfileMapper.fromActiveResponse(
            ActiveProfileResponse(
                profileId = 1L,
                profileName = "   ",
                relationship = "SELF",
                familyId = 3L,
                isPrimary = false,
            ),
        )
        assertEquals("?", profile.initials)
    }
}
