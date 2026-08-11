package sg.edu.nus.iss.canmakan.features.family.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyException
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationResponse

data class InvitationsUiState(
    val isLoading: Boolean = false,
    val invitations: List<PendingInvitationResponse> = emptyList(),
    val actingToken: String? = null,
    val errorMessage: String? = null,
    val acceptedFamilyName: String? = null,
)

@HiltViewModel
class InvitationsViewModel @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvitationsUiState())
    val uiState: StateFlow<InvitationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                acceptedFamilyName = null,
            )
            try {
                val invitations = familyProfileRepository.listMyInvitations()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    invitations = invitations,
                )
            } catch (exception: CreateFamilyException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = exception.message ?: "Could not load invitations.",
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = exception.message ?: "Could not load invitations.",
                )
            }
        }
    }

    fun accept(token: String, onAccepted: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                actingToken = token,
                errorMessage = null,
                acceptedFamilyName = null,
            )
            try {
                val joined = familyProfileRepository.acceptInvitation(token)
                _uiState.value = _uiState.value.copy(
                    actingToken = null,
                    acceptedFamilyName = joined.familyName,
                    invitations = _uiState.value.invitations.filterNot {
                        it.invitationToken == token
                    },
                )
                onAccepted()
            } catch (exception: CreateFamilyException) {
                _uiState.value = _uiState.value.copy(
                    actingToken = null,
                    errorMessage = exception.message ?: "Could not accept invitation.",
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    actingToken = null,
                    errorMessage = exception.message ?: "Could not accept invitation.",
                )
            }
        }
    }

    fun decline(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                actingToken = token,
                errorMessage = null,
            )
            try {
                familyProfileRepository.declineInvitation(token)
                _uiState.value = _uiState.value.copy(
                    actingToken = null,
                    invitations = _uiState.value.invitations.filterNot {
                        it.invitationToken == token
                    },
                )
            } catch (exception: CreateFamilyException) {
                _uiState.value = _uiState.value.copy(
                    actingToken = null,
                    errorMessage = exception.message ?: "Could not decline invitation.",
                )
            } catch (exception: Exception) {
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
}
