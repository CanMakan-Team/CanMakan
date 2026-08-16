package sg.edu.nus.iss.canmakan.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InviteWebDeepLinksTest {

    @Test
    fun uriPatternsUseConfiguredWebOriginsAndCustomScheme() {
        val patterns = InviteWebDeepLinks.uriPatterns(
            "http://localhost:5173, https://canmakan-project.web.app",
        )

        assertEquals(
            listOf(
                "http://localhost:5173/invite/{token}",
                "https://canmakan-project.web.app/invite/{token}",
                "canmakan://invite/{token}",
            ),
            patterns,
        )
    }

    @Test
    fun uriPatternsIgnoreBlankEntries() {
        val patterns = InviteWebDeepLinks.uriPatterns("https://canmakan-project.web.app,")

        assertEquals(2, patterns.size)
        assertTrue(patterns.first().startsWith("https://canmakan-project.web.app/"))
        assertEquals("canmakan://invite/{token}", patterns.last())
    }

    @Test
    fun familyPortalMembersUrlSkipsLocalHosts() {
        val url = InviteWebDeepLinks.familyPortalMembersUrl(
            "http://localhost:5173,https://canmakan-project.web.app",
        )
        assertEquals("https://canmakan-project.web.app/family", url)
    }

    @Test
    fun familyPortalMembersUrlReturnsNullWhenNoPublicHttpsOrigin() {
        val url = InviteWebDeepLinks.familyPortalMembersUrl("http://localhost:5173")
        assertEquals(null, url)
    }

    @Test
    fun familyPortalMembersUrlSkipsLoopbackAndBlankEntries() {
        val url = InviteWebDeepLinks.familyPortalMembersUrl(
            " , http://127.0.0.1:5173, https://canmakan-project.web.app/",
        )
        assertEquals("https://canmakan-project.web.app/family", url)
    }

    @Test
    fun familyPortalMembersUrlIgnoresNonHttpsAndInvalidOrigins() {
        val url = InviteWebDeepLinks.familyPortalMembersUrl(
            "http://example.com,http://[,https://canmakan-project.web.app",
        )
        assertEquals("https://canmakan-project.web.app/family", url)
    }

    @Test
    fun familyPortalMembersUrlReturnsNullWhenOnlyInvalidOrigins() {
        val url = InviteWebDeepLinks.familyPortalMembersUrl(" ,http://127.0.0.1,http://[")
        assertEquals(null, url)
    }
}
