package sg.edu.nus.iss.canmakan.features.family.ui

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.auth.data.CurrentUserSession
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import javax.inject.Inject

/**
 * (UC6) Create a ViewModel to Handle the Business Logic
 * for the Family Restriction Summary Screen.
 */

@HiltViewModel
class FamilyRestrictionSummaryViewModel @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
    private val currentUserSession: CurrentUserSession
) : ViewModel() {
    private val _uiState = MutableStateFlow<FamilyRestrictionSummaryUiState>(FamilyRestrictionSummaryUiState.Loading)
    val uiState: StateFlow<FamilyRestrictionSummaryUiState> = _uiState.asStateFlow()

    fun fetchSummary() {
        val userId = currentUserSession.userId ?: return

        viewModelScope.launch {
            _uiState.value = FamilyRestrictionSummaryUiState.Loading
            val result = familyProfileRepository.getFamilyRestrictionSummary(userId)

            result.fold(
                onSuccess = { response ->
                    val activeMembers = response.familyMembers.filter { it.isActive }
                    if (activeMembers.isEmpty()) {
                        _uiState.value = FamilyRestrictionSummaryUiState.Empty
                    } else {
                        // Extract unique restrictions from the response
                        val uniqueRestrictions = activeMembers
                            .flatMap { it.restrictions }
                            .map { it.displayName }
                            .distinct()

                        _uiState.value = FamilyRestrictionSummaryUiState.Success(
                            response.copy(familyMembers = activeMembers),
                            uniqueRestrictions
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.value = FamilyRestrictionSummaryUiState.Error(
                        error.message ?: "Unknown Error Occurred"
                    )
                }
            )
        }
    }
}