package sg.edu.nus.iss.canmakan.features.product.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestrictionSheetUiState
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.product.history.data.ScanHistoryRepository
import sg.edu.nus.iss.canmakan.features.product.history.model.ScanHistoryScreenUiState
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScanHistoryViewModel @Inject constructor(
    private val activeProfileManager: ActiveProfileManager,
    private val scanHistoryRepo: ScanHistoryRepository
): ViewModel() {
    private val _scanHistoryUiState = MutableStateFlow(ScanHistoryScreenUiState())
    val scanHistoryUiState: StateFlow<ScanHistoryScreenUiState> = _scanHistoryUiState

    init {
        loadScanHistoryForProfile(activeProfileManager.currentProfileId.value)
    }
    private fun loadScanHistoryForProfile(profileId: Long) {
        // Only set isLoading if we don't already have restrictions
        viewModelScope.launch {
            try {
                val savedDietaryRestrictions = scanHistoryRepo.getScanHistoryForProfile(profileId)
            } catch (e: Exception) {
                Timber.e(e, "Error loading scan history for profile $profileId")
                // We don't overwrite the main error message if it was already set by loadDietaryRestrictions
                if (_scanHistoryUiState.value.errorMessage == null) {
                    _scanHistoryUiState.value = _scanHistoryUiState.value.copy(
                        errorMessage = "Unable to load scan history. Please try again."
                    )
                }
            }
        }

    }
}