package sg.edu.nus.iss.canmakan.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthenticatedChromeCallbacksTest {

    @Test
    fun defaultsLeaveNotificationsIdle() {
        var menuClicks = 0
        var scanClicks = 0
        var historyClicks = 0
        val chrome = AuthenticatedChromeCallbacks(
            onMenuClick = { menuClicks++ },
            onScanClick = { scanClicks++ },
            onHistoryClick = { historyClicks++ },
        )

        chrome.onMenuClick()
        chrome.onNotificationsClick()
        chrome.onScanClick()
        chrome.onHistoryClick()

        assertEquals(1, menuClicks)
        assertEquals(1, scanClicks)
        assertEquals(1, historyClicks)
        assertFalse(chrome.hasUnreadNotifications)
    }

    @Test
    fun unreadBadgeAndNotificationCallbackCanBeOverridden() {
        var notificationClicks = 0
        val chrome = AuthenticatedChromeCallbacks(
            onMenuClick = {},
            onNotificationsClick = { notificationClicks++ },
            hasUnreadNotifications = true,
            onScanClick = {},
            onHistoryClick = {},
        )

        chrome.onNotificationsClick()

        assertTrue(chrome.hasUnreadNotifications)
        assertEquals(1, notificationClicks)
    }

    @Test
    fun authenticatedRoutesMatchNavHostDestinations() {
        assertEquals("scanner", AuthenticatedRoutes.SCANNER)
        assertEquals("history", AuthenticatedRoutes.HISTORY)
        assertEquals("product_detail", AuthenticatedRoutes.PRODUCT_DETAIL)
        assertEquals("create_family", AuthenticatedRoutes.CREATE_FAMILY)
        assertEquals("family/manage", AuthenticatedRoutes.MANAGE_FAMILY)
        assertEquals("family/invite", AuthenticatedRoutes.INVITE_MEMBER)
        assertEquals("family/dependant", AuthenticatedRoutes.DEPENDANT_PROFILE)
        assertEquals("notifications", AuthenticatedRoutes.NOTIFICATIONS)
        assertEquals("settings", AuthenticatedRoutes.SETTINGS)
        assertEquals("family/restrictions", AuthenticatedRoutes.FAMILY_RESTRICTIONS)
    }
}
