package sg.edu.nus.iss.canmakan.features.product.history

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestrictionSheetUiState
import sg.edu.nus.iss.canmakan.features.product.history.data.ScanHistoryRepository
import sg.edu.nus.iss.canmakan.features.product.history.model.ScanHistoryScreenUiState
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScanHistoryViewModel @Inject constructor(
    private val activeProfile: DietaryProfile,
    private val scanHistoryRepo: ScanHistoryRepository
): ViewModel() {
    private val _uiState = MutableStateFlow(ScanHistoryScreenUiState())
    val uiState: StateFlow<ScanHistoryScreenUiState> = _uiState

    private suspend fun loadScanHistoryForProfile(profileId: Long) {
        // Only set isLoading if we don't already have restrictions
        try {
            val savedDietaryRestrictions = scanHistoryRepo.getScanHistoryForProfile(profileId)
        } catch (e: Exception) {
            Timber.e(e, "Error loading scan history for profile $profileId")
            // We don't overwrite the main error message if it was already set by loadDietaryRestrictions
            if (_uiState.value.errorMessage == null) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Unable to load scan history. Please try again."
                )
            }
        }
    }
}