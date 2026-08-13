package sg.edu.nus.iss.canmakan.features.family

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Profile relationship display tags")
class ProfileRelationshipDisplayTest {

    @Test
    fun adminViewingOwnRowShowsAdminAndSelf() {
        val tags = ProfileRelationshipDisplay.tags(
            profileId = 77L,
            relationship = "SELF",
            isFamilyAdminProfile = true,
            viewerSelfProfileId = 77L,
            viewerMemberRole = "PRIMARY_ADMIN",
        )
        assertTrue(tags.showAdminTag)
        assertEquals("Self", tags.caption)
        assertEquals("Admin · Self", ProfileRelationshipDisplay.sheetRoleLine(tags))
    }

    @Test
    fun adminViewingInviteeDoesNotShowSelfOrRelationship() {
        val tags = ProfileRelationshipDisplay.tags(
            profileId = 99L,
            relationship = "SELF",
            isFamilyAdminProfile = false,
            viewerSelfProfileId = 77L,
            viewerMemberRole = "PRIMARY_ADMIN",
        )
        assertFalse(tags.showAdminTag)
        assertNull(tags.caption)
    }

    @Test
    fun adminViewingDependantShowsRelationshipOnly() {
        val tags = ProfileRelationshipDisplay.tags(
            profileId = 88L,
            relationship = "CHILD",
            isFamilyAdminProfile = false,
            viewerSelfProfileId = 77L,
            viewerMemberRole = "PRIMARY_ADMIN",
        )
        assertFalse(tags.showAdminTag)
        assertEquals("Child", tags.caption)
    }

    @Test
    fun inviteeViewingOwnRowShowsSelfWithoutAdmin() {
        val tags = ProfileRelationshipDisplay.tags(
            profileId = 99L,
            relationship = "SELF",
            isFamilyAdminProfile = false,
            viewerSelfProfileId = 99L,
            viewerMemberRole = "MEMBER",
        )
        assertFalse(tags.showAdminTag)
        assertEquals("Self", tags.caption)
    }

    @Test
    fun inviteeViewingAdminRowShowsAdminWithoutSelf() {
        val tags = ProfileRelationshipDisplay.tags(
            profileId = 77L,
            relationship = "SELF",
            isFamilyAdminProfile = true,
            viewerSelfProfileId = 99L,
            viewerMemberRole = "MEMBER",
        )
        assertTrue(tags.showAdminTag)
        assertNull(tags.caption)
        assertEquals("Admin", ProfileRelationshipDisplay.sheetRoleLine(tags))
    }

    @Test
    fun inviteeDoesNotSeeDependantRelationship() {
        val tags = ProfileRelationshipDisplay.tags(
            profileId = 88L,
            relationship = "SPOUSE",
            isFamilyAdminProfile = false,
            viewerSelfProfileId = 99L,
            viewerMemberRole = "MEMBER",
        )
        assertFalse(tags.showAdminTag)
        assertNull(tags.caption)
    }
}
