package sg.edu.nus.iss.canmakan.features.family.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FamilyProfileMapperTest {

    // Testing whether backend profile response maps to UI profile model
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
}
