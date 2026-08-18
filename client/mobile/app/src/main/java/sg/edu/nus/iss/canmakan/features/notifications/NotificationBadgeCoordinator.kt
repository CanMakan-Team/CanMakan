package sg.edu.nus.iss.canmakan.features.notifications

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.notifications.data.NotificationsRepository
import sg.edu.nus.iss.canmakan.features.notifications.data.UserNotificationResponse
import sg.edu.nus.iss.canmakan.shared.notifications.SystemNotifier
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationBadgeCoordinator @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
    private val notificationsRepository: NotificationsRepository,
    private val authSessionStore: AuthSessionStore,
    private val systemNotifier: SystemNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _hasUnreadNotifications = MutableStateFlow(false)
    val hasUnreadNotifications: StateFlow<Boolean> = _hasUnreadNotifications.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(false)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _notificationsEnabledError = MutableStateFlow<String?>(null)
    val notificationsEnabledError: StateFlow<String?> = _notificationsEnabledError.asStateFlow()

    private val notifiedNotificationIds = mutableSetOf<Long>()
    private var currentAccountKey: AuthAccountKey? = null
    private var notificationsJob: Job? = null
    private var notificationsEnabledJob: Job? = null
    private var notificationsEnabledGeneration = 0L

    init {
        scope.launch {
            authSessionStore.accountKey.collect(::onAuthenticatedAccountChanged)
        }
    }

    fun clearNotificationsEnabledError() {
        _notificationsEnabledError.value = null
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            _notificationsEnabledError.value = "Sign in before changing notification settings."
            return
        }
        if (_notificationsEnabled.value == enabled) return

        val previous = _notificationsEnabled.value
        notificationsEnabledJob?.cancel()
        val generation = ++notificationsEnabledGeneration
        _notificationsEnabledError.value = null
        _notificationsEnabled.value = enabled
        notificationsEnabledJob = scope.launch {
            try {
                val saved = familyProfileRepository.setNotificationPreference(enabled)
                if (!isCurrentAccount(accountKey) || generation != notificationsEnabledGeneration) return@launch
                _notificationsEnabled.value = saved
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey) || generation != notificationsEnabledGeneration) return@launch
                Timber.w(exception, "Failed to save notification preference")
                _notificationsEnabled.value = previous
                _notificationsEnabledError.value =
                    "Could not save notification setting. Check your connection and try again."
            }
        }
    }

    fun refreshNotifications() {
        val accountKey = authSessionStore.accountKey.value ?: return
        notificationsJob?.cancel()
        notificationsJob = scope.launch { refreshNotifications(accountKey) }
    }

    private fun onAuthenticatedAccountChanged(accountKey: AuthAccountKey?) {
        currentAccountKey = accountKey
        notificationsJob?.cancel()
        notificationsEnabledJob?.cancel()
        notificationsEnabledGeneration++
        _hasUnreadNotifications.value = false
        _notificationsEnabled.value = false
        _notificationsEnabledError.value = null
        notifiedNotificationIds.clear()
        if (accountKey != null) {
            notificationsJob = scope.launch {
                loadNotificationPreference(accountKey)
                refreshNotifications(accountKey)
            }
        }
    }

    private suspend fun loadNotificationPreference(accountKey: AuthAccountKey) {
        val generation = notificationsEnabledGeneration
        try {
            val enabled = familyProfileRepository.getNotificationPreference()
            if (!isCurrentAccount(accountKey) || generation != notificationsEnabledGeneration) return
            _notificationsEnabled.value = enabled
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.w(exception, "Error loading notification preference")
        }
    }

    private suspend fun refreshNotifications(accountKey: AuthAccountKey) {
        try {
            val notifications = notificationsRepository.listMine()
            if (!isCurrentAccount(accountKey)) return
            val unread = notifications.filter { !it.read && !it.expired }
            _hasUnreadNotifications.value = unread.isNotEmpty()
            notifyNewlyUnread(unread)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.w(exception, "Error refreshing notification badge state")
        }
    }

    private fun notifyNewlyUnread(unread: List<UserNotificationResponse>) {
        val newlyUnread = unread.filter { it.id !in notifiedNotificationIds }
        newlyUnread.forEach { notification ->
            systemNotifier.notify(
                id = notification.id.toInt(),
                title = notification.title,
                body = notification.body?.takeIf { it.isNotBlank() }
                    ?: "You have a new update in CanMakan.",
                notificationsEnabled = _notificationsEnabled.value,
            )
        }
        notifiedNotificationIds += newlyUnread.map { it.id }
        notifiedNotificationIds.retainAll(unread.map { it.id }.toSet())
    }

    private fun isCurrentAccount(accountKey: AuthAccountKey): Boolean =
        authSessionStore.accountKey.value == accountKey && currentAccountKey == accountKey
}
