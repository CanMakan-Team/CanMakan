package sg.edu.nus.iss.canmakan.features.family.ui

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
import sg.edu.nus.iss.canmakan.features.family.data.FamilyApiException
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.family.model.RelationshipToAdmin
import javax.inject.Inject

data class InviteFamilyMemberUiState(
    val email: String = "",
    val relationship: RelationshipToAdmin? = null,
    val isInviting: Boolean = false,
    /** One-shot success; screen consumes then clears via [InviteFamilyMemberViewModel.consumeInviteResult]. */
    val inviteSucceeded: Boolean = false,
    val emailSent: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class InviteFamilyMemberViewModel @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
    private val authSessionStore: AuthSessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteFamilyMemberUiState())
    val uiState: StateFlow<InviteFamilyMemberUiState> = _uiState.asStateFlow()
    private var inviteJob: Job? = null
    private var accountObserved = false
    private var observedAccountKey: AuthAccountKey? = null

    init {
        viewModelScope.launch {
            authSessionStore.accountKey
                .collect(::bindAccount)
        }
    }

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email,
            errorMessage = null,
            inviteSucceeded = false,
            emailSent = false,
        )
    }

    fun updateRelationship(value: RelationshipToAdmin) {
        _uiState.value = _uiState.value.copy(
            relationship = value,
            errorMessage = null,
            inviteSucceeded = false,
            emailSent = false,
        )
    }

    fun invite() {
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            bindAccount(null)
            _uiState.value = _uiState.value.copy(errorMessage = "Sign in before inviting a user.")
            return
        }
        bindAccount(accountKey)
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter an email address.")
            return
        }
        val relationship = _uiState.value.relationship
        if (relationship == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Select a relationship.")
            return
        }
        inviteJob?.cancel()
        inviteJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isInviting = true,
                errorMessage = null,
                inviteSucceeded = false,
                emailSent = false,
            )
            try {
                val invitation = familyProfileRepository.createInvitation(
                    email,
                    relationship.name,
                )
                if (!isCurrentAccount(accountKey)) return@launch
                if (invitation.emailSent) {
                    _uiState.value = _uiState.value.copy(
                        isInviting = false,
                        inviteSucceeded = true,
                        emailSent = true,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isInviting = false,
                        inviteSucceeded = false,
                        emailSent = false,
                        errorMessage = "The invitation email could not be sent. Try again in a moment.",
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: FamilyApiException) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isInviting = false,
                    errorMessage = exception.message ?: "Could not create invitation.",
                )
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isInviting = false,
                    errorMessage = "Error: ${exception.message ?: "Could not create invitation."}",
                )
            }
        }
    }

    fun consumeInviteResult() {
        _uiState.value = _uiState.value.copy(
            inviteSucceeded = false,
            emailSent = false,
        )
    }

    private fun isCurrentAccount(accountKey: AuthAccountKey): Boolean =
        authSessionStore.accountKey.value == accountKey

    private fun bindAccount(accountKey: AuthAccountKey?) {
        if (accountObserved && observedAccountKey == accountKey) return
        inviteJob?.cancel()
        _uiState.value = InviteFamilyMemberUiState()
        observedAccountKey = accountKey
        accountObserved = true
    }
}
