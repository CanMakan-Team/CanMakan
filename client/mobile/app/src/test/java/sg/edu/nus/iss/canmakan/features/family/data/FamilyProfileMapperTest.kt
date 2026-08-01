package sg.edu.nus.iss.canmakan.features.family.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyProfileMapperTest {
    @Test
    fun mapsBackendProfileResponseToUiProfile() {
        val response = FamilyProfileResponse(
            id = 7L,
            name = "Noah",
            role = "Child",
            initials = "N"
        )

        val profile = FamilyProfileMapper.fromResponse(response)

        assertEquals(7L, profile.id)
        assertEquals("Noah", profile.profileName)
        assertEquals("Child", profile.relationship)
        assertEquals("N", profile.initials)
    }
}
