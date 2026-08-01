package sg.edu.nus.iss.canmakan.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import timber.log.Timber
import javax.inject.Inject

// ViewModel was created for CanMakanNavGraph solely to access ActiveProfileManager
// Composables cannot access Singleton directly, need to go through ViewModel
@HiltViewModel
class CanMakanNavGraphViewModel @Inject constructor (
    private val activeProfileManager: ActiveProfileManager,
    private val dietaryRestrictionRepo: DietaryRestrictionRepository
): ViewModel() {

    val currentProfileId: StateFlow<Long> = activeProfileManager.currentProfileId

    private val _activeRestrictions = MutableStateFlow<List<String>>(emptyList())
    val activeRestrictions: StateFlow<List<String>> = _activeRestrictions.asStateFlow()

    init {
        viewModelScope.launch {
            currentProfileId.collect { profileId ->
                loadRestrictions(profileId)
            }
        }
    }

    private suspend fun loadRestrictions(profileId: Long) {
        try {
            val allRestrictions = dietaryRestrictionRepo.getAllDietaryRestrictions()
            val profileSelections = dietaryRestrictionRepo.getDietaryRestrictionsForProfile(profileId)
            
            val restrictionNames = allRestrictions
                .filter { profileSelections.containsKey(it.id) }
                .map { it.displayName }
            
            _activeRestrictions.value = restrictionNames
        } catch (e: Exception) {
            Timber.e(e, "Error loading restrictions for profile $profileId")
            _activeRestrictions.value = emptyList()
        }
    }

    fun switchProfile(profileId: Long) {
        activeProfileManager.switchProfile(profileId)
    }

    fun refreshRestrictions() {
        viewModelScope.launch {
            loadRestrictions(currentProfileId.value)
        }
    }
}