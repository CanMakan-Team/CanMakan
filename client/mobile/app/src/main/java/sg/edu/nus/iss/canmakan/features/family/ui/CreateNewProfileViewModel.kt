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
import javax.inject.Inject

data class CreateNewProfileUiState(
    val profileName: String = "",
    val relationship: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val created: Boolean = false,
)

@HiltViewModel
class CreateNewProfileViewModel @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
    private val authSessionStore: AuthSessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateNewProfileUiState())
    val uiState: StateFlow<CreateNewProfileUiState> = _uiState.asStateFlow()
    private var createJob: Job? = null
    private var accountObserved = false
    private var observedAccountKey: AuthAccountKey? = null

    init {
        viewModelScope.launch {
            authSessionStore.accountKey
                .collect(::bindAccount)
        }
    }

    fun updateProfileName(value: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(profileName = value, errorMessage = null)
    }

    fun updateRelationship(value: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(relationship = value, errorMessage = null)
    }

    fun create() {
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            bindAccount(null)
            _uiState.value = _uiState.value.copy(errorMessage = "Sign in before creating a profile.")
            return
        }
        bindAccount(accountKey)
        val state = _uiState.value
        if (state.isSubmitting || state.created) return

        val name = state.profileName.trim()
        val relationship = state.relationship.trim()
        if (name.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Name is required.")
            return
        }
        if (relationship.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Relationship is required.")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        createJob?.cancel()
        createJob = viewModelScope.launch {
            try {
                familyProfileRepository.createDependantProfile(
                    profileName = name,
                    relationship = relationship.uppercase(),
                )
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(isSubmitting = false, created = true)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CreateFamilyException) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = exception.message ?: "Could not create profile.",
                )
            } catch (_: Exception) {
                if (!isCurrentAccount(accountKey)) return@launch
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = "Could not create profile. Check your connection and try again.",
                )
            }
        }
    }

    private fun isCurrentAccount(accountKey: AuthAccountKey): Boolean =
        authSessionStore.accountKey.value == accountKey

    private fun bindAccount(accountKey: AuthAccountKey?) {
        if (accountObserved && observedAccountKey == accountKey) return
        createJob?.cancel()
        _uiState.value = CreateNewProfileUiState()
        observedAccountKey = accountKey
        accountObserved = true
    }
}
