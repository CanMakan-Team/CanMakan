package sg.edu.nus.iss.canmakan.features.family.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    fun fetchSummary() {
        if (authSessionStore.authenticatedUser.value == null) {
            _uiState.value = FamilyRestrictionSummaryUiState.Error("Sign in to view family restrictions.")
            return
        }

        viewModelScope.launch {
            _uiState.value = FamilyRestrictionSummaryUiState.Loading
            val result = familyProfileRepository.getFamilyRestrictionSummary()

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
}
