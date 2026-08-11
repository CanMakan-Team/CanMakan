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
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyException
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.family.data.InvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.UserSearchResponse
import javax.inject.Inject

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
    private val authSessionStore: AuthSessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProfileToFamilyUiState())
    val uiState: StateFlow<AddProfileToFamilyUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
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
            invitation = null,
        )
    }

    fun search() {
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            bindAccount(null)
            _uiState.value = _uiState.value.copy(errorMessage = "Sign in before searching.")
            return
        }
        bindAccount(accountKey)
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter an email address.")
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                errorMessage = null,
                searchResult = null,
                invitation = null,
            )
            try {
                val result = familyProfileRepository.searchUserByEmail(email)
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResult = result,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = exception.message ?: "Search failed.",
                )
            }
        }
    }

    fun createInvite() {
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
        inviteJob?.cancel()
        inviteJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInviting = true, errorMessage = null)
            try {
                val invitation = familyProfileRepository.createInvitation(email)
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isInviting = false,
                    invitation = invitation,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CreateFamilyException) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isInviting = false,
                    errorMessage = exception.message,
                )
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isInviting = false,
                    errorMessage = exception.message ?: "Could not create invitation.",
                )
            }
        }
    }

    private fun isCurrentAccount(accountKey: AuthAccountKey): Boolean =
        authSessionStore.accountKey.value == accountKey

    private fun bindAccount(accountKey: AuthAccountKey?) {
        if (accountObserved && observedAccountKey == accountKey) return
        searchJob?.cancel()
        inviteJob?.cancel()
        _uiState.value = AddProfileToFamilyUiState()
        observedAccountKey = accountKey
        accountObserved = true
    }
}
