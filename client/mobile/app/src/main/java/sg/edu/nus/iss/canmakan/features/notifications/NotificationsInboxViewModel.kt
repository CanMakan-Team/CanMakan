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
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationResponse
import javax.inject.Inject

data class NotificationsInboxUiState(
    val isLoading: Boolean = false,
    val invitations: List<PendingInvitationResponse> = emptyList(),
    val actingToken: String? = null,
    val errorMessage: String? = null,
    val acceptedFamilyName: String? = null,
)

@HiltViewModel
class NotificationsInboxViewModel @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
    private val authSessionStore: AuthSessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsInboxUiState())
    val uiState: StateFlow<NotificationsInboxUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var actionJob: Job? = null
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
                val invitations = familyProfileRepository.listMyInvitations()
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    invitations = invitations,
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
                    invitations = _uiState.value.invitations.filterNot {
                        it.invitationToken == token
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
                    invitations = _uiState.value.invitations.filterNot {
                        it.invitationToken == token
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun isCurrentAccount(accountKey: AuthAccountKey): Boolean =
        authSessionStore.accountKey.value == accountKey

    private fun bindAccount(accountKey: AuthAccountKey?): Boolean {
        if (accountObserved && observedAccountKey == accountKey) return false
        refreshJob?.cancel()
        actionJob?.cancel()
        _uiState.value = NotificationsInboxUiState()
        observedAccountKey = accountKey
        accountObserved = true
        return true
    }
}
