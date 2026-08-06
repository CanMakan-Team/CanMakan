package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestrictionSheetUiState
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DietaryRestrictionViewModel @Inject constructor(
    private val activeProfileManager: ActiveProfileManager,
    private val dietaryRestrictionRepo: DietaryRestrictionRepository,
): ViewModel() {

    // MutableStateFlow always holds a current value and can be updated (hence suitable for UI state)
    // StateFlow is read-only to the UI, so UI can observe, but not change it
    private val _uiState = MutableStateFlow(DietaryRestrictionSheetUiState())
    val uiState: StateFlow<DietaryRestrictionSheetUiState> = _uiState

    init {
        viewModelScope.launch {
            // Load all restrictions once
            loadDietaryRestrictions()
            
            // Then react to profile changes
            activeProfileManager.currentProfileId.collect { profileId ->
                loadDietaryRestrictionsForProfile(profileId)
            }
        }
    }

    private suspend fun loadDietaryRestrictionsForProfile(profileId: Long) {
        // Only set isLoading if we don't already have restrictions
        try {
            val savedDietaryRestrictions = dietaryRestrictionRepo.getDietaryRestrictionsForProfile(profileId)
            _uiState.value = _uiState.value.copy(
                selectedRestrictions = savedDietaryRestrictions,
            )
        } catch (e: Exception) {
            Timber.e(e, "Error loading dietary restrictions for profile $profileId")
            // We don't overwrite the main error message if it was already set by loadDietaryRestrictions
            if (_uiState.value.errorMessage == null) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Unable to load saved restrictions. Please try again."
                )
            }
        }
    }

    suspend fun loadDietaryRestrictions() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        try {
            val allDietaryRestrictions = dietaryRestrictionRepo.getAllDietaryRestrictions()

            val categorized = withContext(Dispatchers.Default) {
                Triple(
                    allDietaryRestrictions.filter { it.category == "RELIGIOUS" },
                    allDietaryRestrictions.filter { it.category == "ALLERGEN" },
                    allDietaryRestrictions.filter { it.category == "DIET" }
                )
            }

            _uiState.value = _uiState.value.copy(
                religiousRestrictions = categorized.first,
                allergenRestrictions = categorized.second,
                dietRestrictions = categorized.third
            )
        } catch (e: Exception) {
            Timber.e(e, "Error loading all dietary restrictions")
            val message = when (e) {
                is java.net.SocketTimeoutException -> "Connection timed out. Please check if the backend server is running at ${sg.edu.nus.iss.canmakan.BuildConfig.BASE_URL ?: "the configured API URL"}"
                is java.net.ConnectException -> "Failed to connect to the server. Please check your network."
                else -> "Unable to load dietary restrictions. Please try again."
            }
            _uiState.value = _uiState.value.copy(errorMessage = message)
        } finally {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
    // This function permits only 1 religious restriction to be selected at any time
    fun selectReligiousRestriction(restrictionId: Long) {
        val currentSelections = _uiState.value.selectedRestrictions.toMutableMap()

        val religiousIds = _uiState.value.religiousRestrictions.map {it.id}
        religiousIds.forEach {id -> currentSelections.remove(id)}

        currentSelections[restrictionId] = "STRICT_AVOID"

        _uiState.value = _uiState.value.copy(selectedRestrictions = currentSelections)
    }
    fun toggleDietaryRestriction(restrictionId: Long) {
        val currentSelections = _uiState.value.selectedRestrictions.toMutableMap()

        if (currentSelections.containsKey(restrictionId)) {
            currentSelections.remove(restrictionId)
        } else {
            currentSelections[restrictionId] = "STRICT_AVOID"
        }

        _uiState.value = _uiState.value.copy(selectedRestrictions = currentSelections)
    }
    
    fun onSave(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val saved = dietaryRestrictionRepo.saveDietaryRestrictionSelections(
                    profileId = activeProfileManager.currentProfileId.value,
                    selections = _uiState.value.selectedRestrictions
                )

                if (saved) {
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Unable to save dietary restrictions. Please try again."
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error saving dietary restrictions")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Unable to save dietary restrictions. Please try again."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
