package sg.edu.nus.iss.canmakan.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyException
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for CanMakanNavGraph — active profile, family membership via /families/me,
 * and drawer-facing family state. Session identity comes from [AuthSessionStore] (UC19).
 */
@HiltViewModel
class CanMakanNavGraphViewModel @Inject constructor(
    private val activeProfileManager: ActiveProfileManager,
    private val dietaryRestrictionRepo: DietaryRestrictionRepository,
    private val familyProfileRepository: FamilyProfileRepository,
    private val authSessionStore: AuthSessionStore,
) : ViewModel() {

    val currentProfileId: StateFlow<Long> = activeProfileManager.currentProfileId

    private val _activeRestrictions = MutableStateFlow<List<String>>(emptyList())
    val activeRestrictions: StateFlow<List<String>> = _activeRestrictions.asStateFlow()

    private val _profiles = MutableStateFlow<List<DietaryProfile>>(emptyList())
    val profiles: StateFlow<List<DietaryProfile>> = _profiles.asStateFlow()

    private val _hasFamily = MutableStateFlow(false)
    val hasFamily: StateFlow<Boolean> = _hasFamily.asStateFlow()

    private val _familyName = MutableStateFlow<String?>(null)
    val familyName: StateFlow<String?> = _familyName.asStateFlow()

    private val _hasUserSession = MutableStateFlow(false)
    val hasUserSession: StateFlow<Boolean> = _hasUserSession.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pendingVerdict = MutableStateFlow<VerdictDetail?>(null)
    val pendingVerdict: StateFlow<VerdictDetail?> = _pendingVerdict.asStateFlow()

    private val _isCreatingFamily = MutableStateFlow(false)
    val isCreatingFamily: StateFlow<Boolean> = _isCreatingFamily.asStateFlow()

    private val _createFamilyError = MutableStateFlow<String?>(null)
    val createFamilyError: StateFlow<String?> = _createFamilyError.asStateFlow()

    /** Manage-member actions stay off until UC9/UC12 APIs exist. */
    val showManageFamilyActions: Boolean = false

    init {
        viewModelScope.launch {
            combine(
                authSessionStore.authenticatedUser,
                currentProfileId,
            ) { user, profileId ->
                user to profileId
            }.collect { (user, profileId) ->
                _hasUserSession.value = user != null
                loadDataWithRetry(profileId)
            }
        }
    }

    private suspend fun loadDataWithRetry(profileId: Long) {
        _isLoading.value = true
        _error.value = null
        try {
            loadFamilyMembershipAndProfiles(profileId)
            val effectiveProfileId = activeProfileManager.currentProfileId.value
            loadRestrictions(effectiveProfileId)
        } finally {
            _isLoading.value = false
        }
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
            val errorMessage = when (e) {
                is java.net.SocketTimeoutException ->
                    "Connection timed out. Check your firewall settings and server connectivity."
                is java.net.ConnectException ->
                    "Could not connect to the server. Please verify the backend is running."
                else -> "Unable to connect to the server. Please check your network and try again."
            }
            _error.value = errorMessage
            _activeRestrictions.value = emptyList()
        }
    }

    private suspend fun loadFamilyMembershipAndProfiles(profileId: Long) {
        if (authSessionStore.authenticatedUser.value == null) {
            _hasFamily.value = false
            _familyName.value = null
            _profiles.value = listOf(personalPlaceholder(profileId))
            return
        }

        try {
            val me = familyProfileRepository.getMyFamily()
            if (me == null) {
                _hasFamily.value = false
                _familyName.value = null
                _profiles.value = listOf(personalPlaceholder(profileId))
                return
            }

            _hasFamily.value = true
            _familyName.value = me.familyName
            val loadedProfiles = familyProfileRepository.getProfilesForFamily(me.familyId)
            _profiles.value = loadedProfiles

            withContext(Dispatchers.Default) {
                val currentId = activeProfileManager.currentProfileId.value
                if (loadedProfiles.none { it.id == currentId }) {
                    activeProfileManager.switchProfile(me.selfProfileId)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading family membership / profiles")
            _error.value = "Unable to connect to the server. Please check your network and try again."
            _hasFamily.value = false
            _familyName.value = null
            _profiles.value = listOf(personalPlaceholder(profileId))
        }
    }

    private fun personalPlaceholder(profileId: Long): DietaryProfile {
        return DietaryProfile(
            id = profileId,
            familyId = 0L,
            profileName = "Personal",
            relationship = "Self",
            initials = "P",
            isPrimary = true,
        )
    }

    fun switchProfile(profileId: Long) {
        activeProfileManager.switchProfile(profileId)
    }

    fun setPendingVerdict(detail: VerdictDetail) {
        _pendingVerdict.value = detail
    }

    fun refreshRestrictions() {
        viewModelScope.launch {
            loadDataWithRetry(currentProfileId.value)
        }
    }

    fun clearCreateFamilyError() {
        _createFamilyError.value = null
    }

    /**
     * UC8 create-circle. Only for authenticated users with no existing family.
     * On success, reloads `/me` + profiles.
     */
    fun createFamilyCircle(familyName: String, onSuccess: () -> Unit) {
        if (authSessionStore.authenticatedUser.value == null) {
            _createFamilyError.value = "Sign in before creating a family circle."
            return
        }
        if (_hasFamily.value) {
            _createFamilyError.value = "You already belong to a family circle."
            return
        }
        viewModelScope.launch {
            _isCreatingFamily.value = true
            _createFamilyError.value = null
            try {
                val created = familyProfileRepository.createFamily(familyName.trim())
                activeProfileManager.switchProfile(created.selfProfileId)
                loadDataWithRetry(created.selfProfileId)
                onSuccess()
            } catch (e: CreateFamilyException) {
                Timber.e(e, "Create family failed")
                _createFamilyError.value = e.message
            } catch (e: Exception) {
                Timber.e(e, "Create family failed")
                _createFamilyError.value =
                    "Unable to create family circle. Check your connection and try again."
            } finally {
                _isCreatingFamily.value = false
            }
        }
    }

    companion object {
        const val NO_FAMILY_MESSAGE =
            "You're not in a family circle yet. Create one here, or use the web Family Portal."

        const val NO_SESSION_FAMILY_MESSAGE =
            "Sign in to create a family circle (or set one up on the web Family Portal)."
    }
}
