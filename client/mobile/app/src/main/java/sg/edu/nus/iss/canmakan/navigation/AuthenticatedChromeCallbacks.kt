package sg.edu.nus.iss.canmakan.navigation

data class AuthenticatedChromeCallbacks(
    val onMenuClick: () -> Unit,
    val onNotificationsClick: () -> Unit = {},
    val hasUnreadNotifications: Boolean = false,
    val onScanClick: () -> Unit,
    val onHistoryClick: () -> Unit,
)

object AuthenticatedRoutes {
    const val SCANNER = "scanner"
    const val HISTORY = "history"
    const val PRODUCT_DETAIL = "product_detail"
    const val CREATE_FAMILY = "create_family"
    const val MANAGE_FAMILY = "family/manage"
    const val INVITE_MEMBER = "family/invite"
    const val DEPENDANT_PROFILE = "family/dependant"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val FAMILY_RESTRICTIONS = "family/restrictions"
}
