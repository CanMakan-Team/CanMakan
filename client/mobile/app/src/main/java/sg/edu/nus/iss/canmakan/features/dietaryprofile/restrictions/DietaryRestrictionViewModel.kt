package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestrictionSheetUiState
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DietaryRestrictionViewModel @Inject constructor(
    private val activeProfileManager: ActiveProfileManager,
    private val dietaryRestrictionRepo: DietaryRestrictionRepository,
    private val familyProfileRepository: FamilyProfileRepository,
) : ViewModel() {

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
                if (profileId != ActiveProfileManager.UNSET_PROFILE_ID) {
                    loadDietaryRestrictionsForProfile(profileId)
                    refreshRestrictionEditPermission(profileId)
                }
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

    /**
     * Resolves D3 edit permission for the active profile so the sheet can lock
     * before a save that would return 403.
     */
    private suspend fun refreshRestrictionEditPermission(profileId: Long) {
        _uiState.value = _uiState.value.copy(
            allowRestrictionEdit = null,
            restrictionEditHint = null,
        )
        try {
            val me = familyProfileRepository.getMyFamily()
            val allow = RestrictionEditAuthorization.mayEditRestrictions(
                profileId = profileId,
                hasFamily = me != null,
                me = me,
            )
            _uiState.value = _uiState.value.copy(
                allowRestrictionEdit = allow,
                restrictionEditHint = if (allow) {
                    null
                } else {
                    RestrictionEditAuthorization.READ_ONLY_HINT
                },
            )
        } catch (e: Exception) {
            Timber.e(e, "Error resolving restriction edit permission for profile $profileId")
            // Fail closed for family profiles: keep the sheet read-only rather than
            // looking editable and failing on save.
            _uiState.value = _uiState.value.copy(
                allowRestrictionEdit = false,
                restrictionEditHint = RestrictionEditAuthorization.READ_ONLY_HINT,
            )
        }
    }

    suspend fun loadDietaryRestrictions() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        try {
            val allDietaryRestrictions = dietaryRestrictionRepo.getAllDietaryRestrictions()

            _uiState.value = _uiState.value.copy(
                religiousRestrictions = allDietaryRestrictions.filter { it.category == "RELIGIOUS" },
                allergenRestrictions = allDietaryRestrictions.filter { it.category == "ALLERGEN" },
                dietRestrictions = allDietaryRestrictions.filter { it.category == "DIET" }
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

    private fun canEditRestrictions(): Boolean = _uiState.value.allowRestrictionEdit == true

    // This function permits only 1 religious restriction to be selected at any time
    fun selectReligiousRestriction(restrictionId: Long) {
        if (!canEditRestrictions()) return
        val currentSelections = _uiState.value.selectedRestrictions.toMutableMap()

        // 1. Check if the user is clicking the currently selected restriction
        val isAlreadySelected = currentSelections.containsKey(restrictionId)

        // 2. Remove all religious restrictions (enforces the max 1 rule)
        val religiousIds = _uiState.value.religiousRestrictions.map { it.id }
        religiousIds.forEach { id -> currentSelections.remove(id) }

        // 3. Only add the restriction if it wasn't already selected
        if (!isAlreadySelected) {
            currentSelections[restrictionId] = "STRICT_AVOID"
        }

        _uiState.value = _uiState.value.copy(selectedRestrictions = currentSelections)
    }

    fun toggleDietaryRestriction(restrictionId: Long) {
        if (!canEditRestrictions()) return
        val currentSelections = _uiState.value.selectedRestrictions.toMutableMap()

        if (currentSelections.containsKey(restrictionId)) {
            currentSelections.remove(restrictionId)
        } else {
            currentSelections[restrictionId] = "STRICT_AVOID"
        }

        _uiState.value = _uiState.value.copy(selectedRestrictions = currentSelections)
    }

    fun onSave(onSuccess: () -> Unit = {}) {
        if (!canEditRestrictions()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = RestrictionEditAuthorization.READ_ONLY_HINT,
            )
            return
        }
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
