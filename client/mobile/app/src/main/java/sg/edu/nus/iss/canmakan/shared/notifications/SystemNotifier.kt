package sg.edu.nus.iss.canmakan.shared.notifications

/**
 * Posts CanMakan alerts to the Android system notification drawer.
 *
 * A plain interface (rather than calling [AndroidSystemNotifier] directly) so callers such
 * as [sg.edu.nus.iss.canmakan.navigation.CanMakanNavGraphViewModel] can be unit tested with a
 * fake, without pulling in Android framework classes.
 */
interface SystemNotifier {
    /**
     * Posts a notification with the given [title]/[body] under [id], unless the user has
     * notifications turned off ([notificationsEnabled] is false) or has not granted the
     * system permission to show them. Neither case is treated as an error: skipping the
     * post is the whole point of the toggle.
     */
    fun notify(id: Int, title: String, body: String, notificationsEnabled: Boolean)
}
