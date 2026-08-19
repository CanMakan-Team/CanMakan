package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProfileRestrictionTest {

    @Test
    fun defaultSeverityIsStrictAvoid() {
        val restriction = ProfileRestriction(dietaryProfileId = 7L, dietaryRestrictionId = 2L)
        assertEquals("STRICT_AVOID", restriction.severityLevel)
        assertEquals(
            restriction,
            ProfileRestriction(7L, 2L, "STRICT_AVOID"),
        )
    }

    @Test
    fun copyCanChangeSeverity() {
        val restriction = ProfileRestriction(1L, 9L, "INTOLERANCE")
        assertEquals("PREFERENCE", restriction.copy(severityLevel = "PREFERENCE").severityLevel)
    }
}
