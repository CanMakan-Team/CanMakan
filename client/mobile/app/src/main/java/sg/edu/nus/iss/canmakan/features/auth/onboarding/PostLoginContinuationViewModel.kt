package sg.edu.nus.iss.canmakan.features.auth.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import sg.edu.nus.iss.canmakan.features.family.data.FamilyApiException
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationStore

sealed interface PostLoginContinuationState {
    data object Checking : PostLoginContinuationState
    data object DietarySetupRequired : PostLoginContinuationState
    data object ClaimingInvitation : PostLoginContinuationState
    data class Ready(val invitationError: String? = null) : PostLoginContinuationState
}

/** Owns every authenticated continuation between UC19 Login and the consumer shell. */
@HiltViewModel
class PostLoginContinuationViewModel @Inject constructor(
    private val authSessionStore: AuthSessionStore,
    private val pendingOnboardingStore: PendingOnboardingStore,
    private val pendingInvitationStore: PendingInvitationStore,
    private val pendingInvitationClaimer: PendingInvitationClaimer,
) : ViewModel() {
    private val _state = MutableStateFlow<PostLoginContinuationState>(
        PostLoginContinuationState.Checking,
    )
    val state: StateFlow<PostLoginContinuationState> = _state.asStateFlow()

    private var claimJob: Job? = null
    private var continuationAccountKey: AuthAccountKey? = null

    init {
        viewModelScope.launch {
            authSessionStore.accountKey.collect { accountKey ->
                val boundAccountKey = continuationAccountKey
                if (boundAccountKey != null && accountKey != boundAccountKey) {
                    claimJob?.cancel()
                    claimJob = null
                    continuationAccountKey = null
                    _state.value = PostLoginContinuationState.Checking
                }
            }
        }
        viewModelScope.launch {
            // The initial value is handled by begin(); only react here to a new deep link
            // arriving after the consumer shell is already ready.
            pendingInvitationStore.token.drop(1).collect { token ->
                val user = currentUser()
                if (token != null && user != null &&
                    continuationAccountKey == authSessionStore.accountKey.value &&
                    _state.value is PostLoginContinuationState.Ready &&
                    pendingOnboardingStore.peekForAccount(user.email) == null
                ) {
                    processPendingInvitation()
                }
            }
        }
    }

    fun begin() {
        val user = currentUser() ?: return invalidateContinuation()
        bindTo(user)
        if (pendingOnboardingStore.peekForAccount(user.email) != null) {
            _state.value = PostLoginContinuationState.DietarySetupRequired
        } else {
            processPendingInvitation()
        }
    }

    fun onDietarySetupResolved() {
        val user = currentUser() ?: return invalidateContinuation()
        if (continuationAccountKey != authSessionStore.accountKey.value) return
        if (pendingOnboardingStore.peekForAccount(user.email) == null) {
            processPendingInvitation()
        }
    }

    fun retryInvitationClaim() {
        processPendingInvitation()
    }

    fun requestDietarySetup() {
        val user = currentUser() ?: return
        bindTo(user)
        pendingOnboardingStore.requestDietarySetup(user.email)
        _state.value = PostLoginContinuationState.DietarySetupRequired
    }

    private fun processPendingInvitation() {
        if (claimJob?.isActive == true) return
        val initiatingUser = currentUser() ?: return invalidateContinuation()
        if (continuationAccountKey != authSessionStore.accountKey.value) return
        val token = pendingInvitationStore.peek()
        if (token == null) {
            _state.value = PostLoginContinuationState.Ready()
            return
        }
        _state.value = PostLoginContinuationState.ClaimingInvitation
        claimJob = viewModelScope.launch {
            try {
                pendingInvitationClaimer.claim(token)
                if (!isCurrentAccount(initiatingUser)) return@launch
                val clearedCurrentToken = pendingInvitationStore.clearIfCurrent(token)
                _state.value = PostLoginContinuationState.Ready()
                if (!clearedCurrentToken && pendingInvitationStore.peek() != null) {
                    claimJob = null
                    processPendingInvitation()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: FamilyApiException) {
                if (!isCurrentAccount(initiatingUser)) return@launch
                _state.value = PostLoginContinuationState.Ready(
                    invitationError(exception.statusCode),
                )
            } catch (_: Exception) {
                if (!isCurrentAccount(initiatingUser)) return@launch
                _state.value = PostLoginContinuationState.Ready(
                    "Could not accept the family invitation. Retry when your connection is available.",
                )
            }
        }
    }

    private fun bindTo(user: AuthenticatedUser) {
        val accountKey = authSessionStore.accountKey.value
            ?.takeIf { it.userId == user.userId }
            ?: return invalidateContinuation()
        if (continuationAccountKey != null && continuationAccountKey != accountKey) {
            claimJob?.cancel()
            claimJob = null
        }
        continuationAccountKey = accountKey
    }

    private fun currentUser(): AuthenticatedUser? = authSessionStore.authenticatedUser.value

    private fun isCurrentAccount(user: AuthenticatedUser): Boolean {
        val accountKey = continuationAccountKey ?: return false
        return accountKey.userId == user.userId &&
            authSessionStore.accountKey.value == accountKey &&
            currentUser()?.userId == user.userId
    }

    private fun invalidateContinuation() {
        claimJob?.cancel()
        claimJob = null
        continuationAccountKey = null
        _state.value = PostLoginContinuationState.Checking
    }

    private fun invitationError(statusCode: Int): String = when (statusCode) {
        403 -> "This invitation does not match the signed-in account."
        404 -> "This family invitation could not be found."
        409 -> "This invitation cannot be accepted in the account's current family state."
        410 -> "This family invitation has expired."
        else -> "Could not accept the family invitation. Retry when your connection is available."
    }
}
