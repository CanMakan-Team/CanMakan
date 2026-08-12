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
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import javax.inject.Inject

/**
 * (UC6) Family restriction summary screen.
 */
@HiltViewModel
class FamilyRestrictionSummaryViewModel @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
    private val authSessionStore: AuthSessionStore,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow<FamilyRestrictionSummaryUiState>(FamilyRestrictionSummaryUiState.Loading)
    val uiState: StateFlow<FamilyRestrictionSummaryUiState> = _uiState.asStateFlow()
    private var fetchJob: Job? = null
    private var accountObserved = false
    private var observedAccountKey: AuthAccountKey? = null

    init {
        viewModelScope.launch {
            authSessionStore.accountKey
                .collect(::bindAccount)
        }
    }

    fun fetchSummary() {
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            _uiState.value = FamilyRestrictionSummaryUiState.Error("Sign in to view family restrictions.")
            return
        }
        bindAccount(accountKey)

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = FamilyRestrictionSummaryUiState.Loading
            val result = try {
                familyProfileRepository.getFamilyRestrictionSummary()
            } catch (exception: CancellationException) {
                throw exception
            }
            if (!isCurrentAccount(accountKey)) return@launch

            result.fold(
                onSuccess = { response ->
                    val activeMembers = response.familyMembers.filter { it.isActive }
                    if (activeMembers.isEmpty()) {
                        _uiState.value = FamilyRestrictionSummaryUiState.Empty
                    } else {
                        val uniqueRestrictions = activeMembers
                            .flatMap { it.restrictions }
                            .map { it.displayName }
                            .distinct()

                        _uiState.value = FamilyRestrictionSummaryUiState.Success(
                            response.copy(familyMembers = activeMembers),
                            uniqueRestrictions,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.value = FamilyRestrictionSummaryUiState.Error(
                        error.message ?: "Unknown Error Occurred",
                    )
                },
            )
        }
    }

    private fun isCurrentAccount(accountKey: AuthAccountKey): Boolean =
        authSessionStore.accountKey.value == accountKey

    private fun bindAccount(accountKey: AuthAccountKey?) {
        if (accountObserved && observedAccountKey == accountKey) return
        fetchJob?.cancel()
        _uiState.value = if (accountKey == null) {
            FamilyRestrictionSummaryUiState.Error("Sign in to view family restrictions.")
        } else {
            FamilyRestrictionSummaryUiState.Loading
        }
        observedAccountKey = accountKey
        accountObserved = true
    }
}
