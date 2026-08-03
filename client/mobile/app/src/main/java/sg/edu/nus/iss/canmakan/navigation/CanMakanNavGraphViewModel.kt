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
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import timber.log.Timber
import javax.inject.Inject

// ViewModel was created for CanMakanNavGraph solely to access ActiveProfileManager
// Composables cannot access Singleton directly, need to go through ViewModel
@HiltViewModel
class CanMakanNavGraphViewModel @Inject constructor (
    private val activeProfileManager: ActiveProfileManager,
    private val dietaryRestrictionRepo: DietaryRestrictionRepository,
    private val familyProfileRepository: FamilyProfileRepository
): ViewModel() {

    val currentProfileId: StateFlow<Long> = activeProfileManager.currentProfileId

    private val _activeRestrictions = MutableStateFlow<List<String>>(emptyList())
    val activeRestrictions: StateFlow<List<String>> = _activeRestrictions.asStateFlow()

    private val _profiles = MutableStateFlow<List<DietaryProfile>>(emptyList())
    val profiles: StateFlow<List<DietaryProfile>> = _profiles.asStateFlow()

    init {
        viewModelScope.launch {
            currentProfileId.collect { profileId ->
                loadDataWithRetry(profileId)
            }
        }
    }

    private suspend fun loadDataWithRetry(profileId: Long) {
        var success = false
        var tryCount = 0
        val maxRetry = 3

        while (!success && tryCount <= maxRetry) {
            try {
                loadRestrictions(profileId)
                loadProfilesForFamily(profileId)
                success = true
            } catch (e: Exception) {
                tryCount++
                if (tryCount <= maxRetry) {
                    Timber.w("Failed to load data for profile $profileId, retrying in ${tryCount * 2}s... ($tryCount/$maxRetry)")
                    kotlinx.coroutines.delay(tryCount * 2000L)
                } else {
                    Timber.e(e, "Final failure loading data for profile $profileId")
                }
            }
        }
    }

    private suspend fun loadRestrictions(profileId: Long) {
        val allRestrictions = dietaryRestrictionRepo.getAllDietaryRestrictions()
        val profileSelections = dietaryRestrictionRepo.getDietaryRestrictionsForProfile(profileId)
        
        val restrictionNames = allRestrictions
            .filter { profileSelections.containsKey(it.id) }
            .map { it.displayName }
        
        _activeRestrictions.value = restrictionNames
    }

    private suspend fun loadProfilesForFamily(profileId: Long) {
        val familyId = 1L
        val loadedProfiles = familyProfileRepository.getProfilesForFamily(familyId)
        _profiles.value = loadedProfiles

        if (loadedProfiles.none { it.id == profileId }) {
            activeProfileManager.switchProfile(loadedProfiles.firstOrNull()?.id ?: profileId)
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