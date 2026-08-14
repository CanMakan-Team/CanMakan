package sg.edu.nus.iss.canmakan.features.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyException
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.notifications.data.NotificationsRepository
import sg.edu.nus.iss.canmakan.features.notifications.data.UserNotificationResponse
import javax.inject.Inject

data class NotificationsInboxUiState(
    val isLoading: Boolean = false,
    val notifications: List<UserNotificationResponse> = emptyList(),
    val actingToken: String? = null,
    val deletingId: Long? = null,
    val isMarkingAllRead: Boolean = false,
    val errorMessage: String? = null,
    val acceptedFamilyName: String? = null,
)

@HiltViewModel
class NotificationsInboxViewModel @Inject constructor(
    private val notificationsRepository: NotificationsRepository,
    private val familyProfileRepository: FamilyProfileRepository,
    private val authSessionStore: AuthSessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsInboxUiState())
    val uiState: StateFlow<NotificationsInboxUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var actionJob: Job? = null
    private var markAllReadJob: Job? = null
    private var accountObserved = false
    private var observedAccountKey: AuthAccountKey? = null

    init {
        viewModelScope.launch {
            authSessionStore.accountKey
                .collect { accountKey ->
                    if (bindAccount(accountKey) && accountKey != null) startRefresh(accountKey)
                }
        }
    }

    fun refresh() {
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            bindAccount(null)
            _uiState.value = NotificationsInboxUiState(errorMessage = "Sign in to view notifications.")
            return
        }
        bindAccount(accountKey)
        startRefresh(accountKey)
    }

    private fun startRefresh(accountKey: AuthAccountKey) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                acceptedFamilyName = null,
            )
            try {
                // Notifications are listed as-is; opening the inbox no longer marks them read
                // automatically, so a glance at the panel doesn't silently dismiss anything the
                // user hasn't acted on yet.
                val notifications = notificationsRepository.listMine()
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    notifications = notifications,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = exception.message ?: "Could not load notifications.",
                )
            }
        }
    }

    fun accept(token: String, onAccepted: () -> Unit) {
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            bindAccount(null)
            _uiState.value = _uiState.value.copy(errorMessage = "Sign in to accept invitations.")
            return
        }
        bindAccount(accountKey)
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                actingToken = token,
                errorMessage = null,
                acceptedFamilyName = null,
            )
            try {
                val joined = familyProfileRepository.acceptInvitation(token)
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    actingToken = null,
                    acceptedFamilyName = joined.familyName,
                    notifications = _uiState.value.notifications.filterNot {
                        it.actionToken == token
                    },
                )
                onAccepted()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CreateFamilyException) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    actingToken = null,
                    errorMessage = exception.message ?: "Could not accept invitation.",
                )
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    actingToken = null,
                    errorMessage = exception.message ?: "Could not accept invitation.",
                )
            }
        }
    }

    fun decline(token: String) {
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            bindAccount(null)
            _uiState.value = _uiState.value.copy(errorMessage = "Sign in to decline invitations.")
            return
        }
        bindAccount(accountKey)
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actingToken = token, errorMessage = null)
            try {
                familyProfileRepository.declineInvitation(token)
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    actingToken = null,
                    notifications = _uiState.value.notifications.filterNot {
                        it.actionToken == token
                    },
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    actingToken = null,
                    errorMessage = exception.message ?: "Could not decline invitation.",
                )
            }
        }
    }

    fun delete(notificationId: Long) {
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            bindAccount(null)
            _uiState.value = _uiState.value.copy(errorMessage = "Sign in to delete notifications.")
            return
        }
        bindAccount(accountKey)
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            val remaining = _uiState.value.notifications.filterNot { it.id == notificationId }
            _uiState.value = _uiState.value.copy(
                deletingId = notificationId,
                errorMessage = null,
                notifications = remaining,
            )
            try {
                notificationsRepository.delete(notificationId)
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(deletingId = null)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    deletingId = null,
                    errorMessage = exception.message ?: "Could not delete notification.",
                )
                startRefresh(accountKey)
            }
        }
    }

    /** Marks every notification read on the user's explicit request (the "Mark All As Read" button). */
    fun markAllRead(onMarked: () -> Unit = {}) {
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            bindAccount(null)
            _uiState.value = _uiState.value.copy(errorMessage = "Sign in to update notifications.")
            return
        }
        bindAccount(accountKey)
        if (_uiState.value.notifications.none { !it.read }) return
        markAllReadJob?.cancel()
        markAllReadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMarkingAllRead = true, errorMessage = null)
            try {
                notificationsRepository.markAllRead()
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isMarkingAllRead = false,
                    notifications = _uiState.value.notifications.map { it.copy(read = true) },
                )
                onMarked()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isMarkingAllRead = false,
                    errorMessage = exception.message ?: "Could not mark notifications as read.",
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun isCurrentAccount(accountKey: AuthAccountKey): Boolean =
        authSessionStore.accountKey.value == accountKey

    private fun bindAccount(accountKey: AuthAccountKey?): Boolean {
        if (accountObserved && observedAccountKey == accountKey) return false
        refreshJob?.cancel()
        actionJob?.cancel()
        markAllReadJob?.cancel()
        _uiState.value = NotificationsInboxUiState()
        observedAccountKey = accountKey
        accountObserved = true
        return true
    }
}
