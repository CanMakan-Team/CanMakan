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
import sg.edu.nus.iss.canmakan.features.family.data.InvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.UserSearchResponse

data class AddProfileToFamilyUiState(
    val email: String = "",
    val isSearching: Boolean = false,
    val isInviting: Boolean = false,
    val searchResult: UserSearchResponse? = null,
    val invitation: InvitationResponse? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class AddProfileToFamilyViewModel @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProfileToFamilyUiState())
    val uiState: StateFlow<AddProfileToFamilyUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email,
            errorMessage = null,
            invitation = null,
        )
    }

    fun search() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter an email address.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                errorMessage = null,
                searchResult = null,
                invitation = null,
            )
            try {
                val result = familyProfileRepository.searchUserByEmail(email)
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResult = result,
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = exception.message ?: "Search failed.",
                )
            }
        }
    }

    fun createInvite() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter an email address.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInviting = true, errorMessage = null)
            try {
                val invitation = familyProfileRepository.createInvitation(email)
                _uiState.value = _uiState.value.copy(
                    isInviting = false,
                    invitation = invitation,
                )
            } catch (exception: CreateFamilyException) {
                _uiState.value = _uiState.value.copy(
                    isInviting = false,
                    errorMessage = exception.message,
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isInviting = false,
                    errorMessage = exception.message ?: "Could not create invitation.",
                )
            }
        }
    }
}
