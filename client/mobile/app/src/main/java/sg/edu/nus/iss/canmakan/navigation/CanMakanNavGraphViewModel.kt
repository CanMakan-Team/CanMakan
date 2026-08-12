package sg.edu.nus.iss.canmakan.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.family.data.ActiveProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyException
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import timber.log.Timber
import javax.inject.Inject

/**
 * Activity-owned state for the authenticated navigation shell.
 *
 * Every remote result is tied to the authenticated account that initiated it. Profile-specific
 * results are additionally tied to [ActiveProfileManager.Selection]. This ViewModel may outlive
 * an authenticated Compose subtree, so account transitions must invalidate its state explicitly.
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

    private val _showManageFamilyActions = MutableStateFlow(false)
    val showManageFamilyActions: StateFlow<Boolean> = _showManageFamilyActions.asStateFlow()

    private val _switchProfileError = MutableStateFlow<String?>(null)
    val switchProfileError: StateFlow<String?> = _switchProfileError.asStateFlow()

    private val _isSwitchingProfile = MutableStateFlow(false)
    val isSwitchingProfile: StateFlow<Boolean> = _isSwitchingProfile.asStateFlow()

    private val reloadMutex = Mutex()
    private var observedAccount = false
    private var currentAccountKey: AuthAccountKey? = null
    private var switchJob: Job? = null
    private var createFamilyJob: Job? = null
    private var refreshJob: Job? = null
    private var accountReloadJob: Job? = null
    private var restrictionJob: Job? = null
    private var switchGeneration = 0L
    private var createFamilyGeneration = 0L

    init {
        viewModelScope.launch {
            authSessionStore.accountKey
                .collect(::onAuthenticatedAccountChanged)
        }
        viewModelScope.launch {
            combine(
                authSessionStore.accountKey,
                activeProfileManager.selection,
            ) { accountKey, selection ->
                selection?.takeIf { accountKey == it.accountKey }
            }
                .distinctUntilChanged()
                .collect { owner ->
                    restrictionJob?.cancel()
                    _activeRestrictions.value = emptyList()
                    if (owner != null) {
                        restrictionJob = viewModelScope.launch { loadRestrictions(owner) }
                    }
                }
        }
    }

    private fun onAuthenticatedAccountChanged(accountKey: AuthAccountKey?) {
        val accountChanged = observedAccount && currentAccountKey != accountKey
        observedAccount = true
        currentAccountKey = accountKey

        if (accountChanged || activeProfileManager.selection.value?.accountKey != accountKey) {
            activeProfileManager.reset()
        }
        switchGeneration++
        createFamilyGeneration++
        switchJob?.cancel()
        createFamilyJob?.cancel()
        refreshJob?.cancel()
        accountReloadJob?.cancel()
        restrictionJob?.cancel()
        clearAccountScopedState(hasSession = accountKey != null)

        if (accountKey != null) {
            accountReloadJob = viewModelScope.launch { reloadFamilyContext(accountKey) }
        } else {
            _isLoading.value = false
        }
    }

    private fun clearAccountScopedState(hasSession: Boolean) {
        _hasUserSession.value = hasSession
        _activeRestrictions.value = emptyList()
        _profiles.value = emptyList()
        _hasFamily.value = false
        _familyName.value = null
        _showManageFamilyActions.value = false
        _pendingVerdict.value = null
        _error.value = null
        _createFamilyError.value = null
        _switchProfileError.value = null
        _isCreatingFamily.value = false
        _isSwitchingProfile.value = false
        _isLoading.value = hasSession
    }

    fun clearSwitchProfileError() {
        _switchProfileError.value = null
    }

    private suspend fun reloadFamilyContext(accountKey: AuthAccountKey) {
        reloadMutex.withLock {
            if (!isCurrentAccount(accountKey)) return
            _isLoading.value = true
            _error.value = null
            try {
                val me = familyProfileRepository.getMyFamily()
                if (!isCurrentAccount(accountKey)) return

                val loadedProfiles = if (me != null) {
                    familyProfileRepository.getProfilesForFamily(me.familyId)
                } else {
                    emptyList()
                }
                if (!isCurrentAccount(accountKey)) return

                val activeFromServer = try {
                    familyProfileRepository.getActiveProfile()
                } catch (exception: CreateFamilyException) {
                    if (exception.statusCode == 404) null else throw exception
                }
                if (!isCurrentAccount(accountKey)) return

                _hasFamily.value = me != null
                _familyName.value = me?.familyName
                _showManageFamilyActions.value = me?.memberRole == "PRIMARY_ADMIN"

                if (activeFromServer == null) {
                    activeProfileManager.selection.value
                        ?.takeIf { it.accountKey == accountKey }
                        ?.let { activeProfileManager.reset() }
                    _profiles.value = emptyList()
                    return
                }

                require(activeFromServer.profileId > 0) {
                    "Active-profile response must contain a positive profile id."
                }
                val resolvedProfileId = if (me == null) {
                    activeFromServer.profileId
                } else {
                    resolveActiveProfileId(
                        serverProfileId = activeFromServer.profileId,
                        loadedProfiles = loadedProfiles,
                        selfProfileId = me.selfProfileId,
                    )
                }
                require(resolvedProfileId > 0) { "Resolved active profile id must be positive." }
                if (!isCurrentAccount(accountKey)) return

                val activeProfile = profileFromActiveResponse(activeFromServer)
                _profiles.value = if (me == null) {
                    listOf(activeProfile)
                } else {
                    loadedProfiles.ifEmpty { listOf(activeProfile) }
                }
                applyActiveProfileId(accountKey, resolvedProfileId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey)) return
                Timber.e(exception, "Error loading family membership / profiles")
                _error.value =
                    "Unable to connect to the server. Please check your network and try again."
                _hasFamily.value = false
                _familyName.value = null
                _showManageFamilyActions.value = false
                _profiles.value = emptyList()
            } finally {
                if (isCurrentAccount(accountKey)) _isLoading.value = false
            }
        }
    }

    private suspend fun loadRestrictions(owner: ActiveProfileManager.Selection) {
        try {
            val allRestrictions = dietaryRestrictionRepo.getAllDietaryRestrictions()
            if (!isCurrentOwner(owner)) return
            val profileSelections =
                dietaryRestrictionRepo.getDietaryRestrictionsForProfile(owner.profileId)
            if (!isCurrentOwner(owner)) return

            val restrictionNames = allRestrictions
                .filter { profileSelections.containsKey(it.id) }
                .map { it.displayName }
            if (!isCurrentOwner(owner)) return
            _activeRestrictions.value = restrictionNames
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (!isCurrentOwner(owner)) return
            Timber.e(exception, "Error loading restrictions for active profile")
            _error.value = when (exception) {
                is java.net.SocketTimeoutException ->
                    "Connection timed out. Check your firewall settings and server connectivity."
                is java.net.ConnectException ->
                    "Could not connect to the server. Please verify the backend is running."
                else -> "Unable to connect to the server. Please check your network and try again."
            }
            _activeRestrictions.value = emptyList()
        }
    }

    private fun applyActiveProfileId(accountKey: AuthAccountKey, profileId: Long) {
        if (!isCurrentAccount(accountKey)) return
        require(profileId > 0) { "Active profile id must be positive." }
        if (!activeProfileManager.isCurrent(accountKey, profileId)) {
            activeProfileManager.switchProfile(accountKey, profileId)
        }
    }

    private fun resolveActiveProfileId(
        serverProfileId: Long,
        loadedProfiles: List<DietaryProfile>,
        selfProfileId: Long?,
    ): Long {
        if (loadedProfiles.any { it.id == serverProfileId }) return serverProfileId
        if (selfProfileId != null && loadedProfiles.any { it.id == selfProfileId }) {
            return selfProfileId
        }
        return loadedProfiles.firstOrNull()?.id ?: serverProfileId
    }

    private fun profileFromActiveResponse(active: ActiveProfileResponse): DietaryProfile {
        val trimmedName = active.profileName.trim()
        val words = trimmedName.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val initials = if (words.size >= 2) {
            (words.first().take(1) + words.last().take(1)).uppercase()
        } else {
            trimmedName.take(minOf(2, trimmedName.length)).uppercase()
        }
        return DietaryProfile(
            id = active.profileId,
            familyId = active.familyId ?: 0L,
            profileName = active.profileName,
            relationship = active.relationship.orEmpty(),
            initials = initials.ifEmpty { "?" },
            isPrimary = active.isPrimary ?: false,
        )
    }

    fun switchProfile(profileId: Long) {
        if (profileId <= 0) {
            _switchProfileError.value = "Complete profile setup before selecting a profile."
            return
        }
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            _switchProfileError.value = "Sign in before selecting a profile."
            return
        }
        if (activeProfileManager.isCurrent(accountKey, profileId)) return

        val expectedSelection = activeProfileManager.selection.value
        switchJob?.cancel()
        val generation = ++switchGeneration
        switchJob = viewModelScope.launch {
            _isSwitchingProfile.value = true
            _switchProfileError.value = null
            try {
                val selected = familyProfileRepository.setActiveProfile(profileId)
                if (!isCurrentAccount(accountKey) ||
                    generation != switchGeneration ||
                    activeProfileManager.selection.value != expectedSelection
                ) return@launch
                require(selected.profileId == profileId && selected.profileId > 0) {
                    "Active-profile update returned an unexpected profile id."
                }
                applyActiveProfileId(accountKey, selected.profileId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CreateFamilyException) {
                if (!isCurrentAccount(accountKey) || generation != switchGeneration) return@launch
                Timber.w(exception, "Switch profile failed")
                _switchProfileError.value = when (exception.statusCode) {
                    403 -> "That profile is not in your family circle."
                    409 -> "That profile is inactive and cannot be selected."
                    else -> exception.message ?: "Could not switch profile. Please try again."
                }
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey) || generation != switchGeneration) return@launch
                Timber.w(exception, "Switch profile failed")
                _switchProfileError.value =
                    "Could not switch profile. Check your connection and try again."
            } finally {
                if (isCurrentAccount(accountKey) && generation == switchGeneration) {
                    _isSwitchingProfile.value = false
                }
            }
        }
    }

    fun setPendingVerdict(profileId: Long, detail: VerdictDetail) {
        val accountKey = authSessionStore.accountKey.value ?: return
        if (activeProfileManager.isCurrent(accountKey, profileId)) {
            _pendingVerdict.value = detail
        }
    }

    fun refreshRestrictions() {
        val accountKey = authSessionStore.accountKey.value ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            reloadFamilyContext(accountKey)
            activeProfileManager.selection.value
                ?.takeIf { isCurrentOwner(it) }
                ?.let { loadRestrictions(it) }
        }
    }

    fun clearCreateFamilyError() {
        _createFamilyError.value = null
    }

    /** UC8 create-circle. The backend may bootstrap the caller's missing SELF profile. */
    fun createFamilyCircle(familyName: String, onSuccess: () -> Unit) {
        val accountKey = authSessionStore.accountKey.value
        if (accountKey == null) {
            _createFamilyError.value = "Sign in before creating a family circle."
            return
        }
        if (_hasFamily.value) {
            _createFamilyError.value = "You already belong to a family circle."
            return
        }

        createFamilyJob?.cancel()
        val generation = ++createFamilyGeneration
        createFamilyJob = viewModelScope.launch {
            _isCreatingFamily.value = true
            _createFamilyError.value = null
            try {
                val created = familyProfileRepository.createFamily(familyName.trim())
                if (!isCurrentAccount(accountKey) || generation != createFamilyGeneration) {
                    return@launch
                }
                require(created.selfProfileId > 0) {
                    "Family creation must return a positive SELF profile id."
                }
                applyActiveProfileId(accountKey, created.selfProfileId)
                reloadFamilyContext(accountKey)
                if (isCurrentAccount(accountKey) && generation == createFamilyGeneration) {
                    onSuccess()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CreateFamilyException) {
                if (!isCurrentAccount(accountKey) || generation != createFamilyGeneration) return@launch
                Timber.e(exception, "Create family failed")
                _createFamilyError.value = exception.message
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey) || generation != createFamilyGeneration) return@launch
                Timber.e(exception, "Create family failed")
                _createFamilyError.value =
                    "Unable to create family circle. Check your connection and try again."
            } finally {
                if (isCurrentAccount(accountKey) && generation == createFamilyGeneration) {
                    _isCreatingFamily.value = false
                }
            }
        }
    }

    private fun isCurrentAccount(accountKey: AuthAccountKey): Boolean =
        authSessionStore.accountKey.value == accountKey && currentAccountKey == accountKey

    private fun isCurrentOwner(owner: ActiveProfileManager.Selection): Boolean =
        isCurrentAccount(owner.accountKey) &&
            activeProfileManager.isCurrent(owner.accountKey, owner.profileId)

    companion object {
        const val NO_FAMILY_MESSAGE =
            "You're not in a family circle yet. Create one here, or use the web Family Portal."

        const val NO_SESSION_FAMILY_MESSAGE =
            "Sign in to create a family circle (or set one up on the web Family Portal)."
    }
}
