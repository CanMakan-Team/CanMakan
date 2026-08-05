package sg.edu.nus.iss.canmakan.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            currentProfileId.collect { profileId ->
                _isLoading.value = true
                _error.value = null
                try {
                    loadDataWithRetry(profileId)
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun loadDataWithRetry(profileId: Long) {
        loadRestrictions(profileId)
        loadProfilesForFamily(profileId)
    }

    private suspend fun loadRestrictions(profileId: Long) {
        try {
            val allRestrictions = dietaryRestrictionRepo.getAllDietaryRestrictions()
            val profileSelections = dietaryRestrictionRepo.getDietaryRestrictionsForProfile(profileId)

            val restrictionNames = withContext(Dispatchers.Default) {
                allRestrictions
                    .filter { profileSelections.containsKey(it.id) }
                    .map { it.displayName }
            }

            _activeRestrictions.value = restrictionNames
        } catch (e: Exception) {
            Timber.e(e, "Error loading restrictions for profile $profileId")
            _error.value = "Unable to connect to the server. Please check your network and try again."
            _activeRestrictions.value = emptyList()
        }
    }

    private suspend fun loadProfilesForFamily(profileId: Long) {
        val familyId = 1L
        try {
            val loadedProfiles = familyProfileRepository.getProfilesForFamily(familyId)
            _profiles.value = loadedProfiles

            withContext(Dispatchers.Default) {
                if (loadedProfiles.none { it.id == profileId }) {
                    activeProfileManager.switchProfile(loadedProfiles.firstOrNull()?.id ?: profileId)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading profiles for family")
            _error.value = "Unable to connect to the server. Please check your network and try again."
            _profiles.value = emptyList()
        }
    }

    fun switchProfile(profileId: Long) {
        activeProfileManager.switchProfile(profileId)
    }

    fun refreshRestrictions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            loadRestrictions(currentProfileId.value)
            loadProfilesForFamily(currentProfileId.value)
            _isLoading.value = false
        }
    }
}