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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.family.FamilyContextLoader
import sg.edu.nus.iss.canmakan.features.family.FamilyShellSnapshot
import sg.edu.nus.iss.canmakan.features.family.data.FamilyApiException
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.notifications.NotificationBadgeCoordinator
import sg.edu.nus.iss.canmakan.features.product.PendingVerdictHolder
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.util.userMessageForNetworkFailure
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
    private val familyContextLoader: FamilyContextLoader,
    private val notificationBadge: NotificationBadgeCoordinator,
    private val pendingVerdictHolder: PendingVerdictHolder,
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

    val pendingVerdict: StateFlow<VerdictDetail?> = pendingVerdictHolder.pendingVerdict

    private val _isCreatingFamily = MutableStateFlow(false)
    val isCreatingFamily: StateFlow<Boolean> = _isCreatingFamily.asStateFlow()

    private val _createFamilyError = MutableStateFlow<String?>(null)
    val createFamilyError: StateFlow<String?> = _createFamilyError.asStateFlow()

    private val _showManageFamilyActions = MutableStateFlow(false)
    val showManageFamilyActions: StateFlow<Boolean> = _showManageFamilyActions.asStateFlow()

    private val _selfProfileId = MutableStateFlow<Long?>(null)
    val selfProfileId: StateFlow<Long?> = _selfProfileId.asStateFlow()

    private val _memberRole = MutableStateFlow<String?>(null)
    val memberRole: StateFlow<String?> = _memberRole.asStateFlow()

    private val _switchProfileError = MutableStateFlow<String?>(null)
    val switchProfileError: StateFlow<String?> = _switchProfileError.asStateFlow()

    private val _isSwitchingProfile = MutableStateFlow(false)
    val isSwitchingProfile: StateFlow<Boolean> = _isSwitchingProfile.asStateFlow()

    val hasUnreadNotifications: StateFlow<Boolean> = notificationBadge.hasUnreadNotifications
    val notificationsEnabled: StateFlow<Boolean> = notificationBadge.notificationsEnabled
    val notificationsEnabledError: StateFlow<String?> = notificationBadge.notificationsEnabledError

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
        _selfProfileId.value = null
        _memberRole.value = null
        pendingVerdictHolder.clear()
        _error.value = null
        _createFamilyError.value = null
        _switchProfileError.value = null
        _isCreatingFamily.value = false
        _isSwitchingProfile.value = false
        _isLoading.value = hasSession
    }

    fun clearNotificationsEnabledError() {
        notificationBadge.clearNotificationsEnabledError()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationBadge.setNotificationsEnabled(enabled)
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
                val snapshot = familyContextLoader.load(accountKey) { isCurrentAccount(accountKey) }
                    ?: return
                if (!isCurrentAccount(accountKey)) return
                applyFamilySnapshot(snapshot)
                snapshot.resolvedProfileId?.let { applyActiveProfileId(accountKey, it) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey)) return
                Timber.e(exception, "Error loading family membership / profiles")
                _error.value =
                    "Unable to connect to the server. Please check your network and try again."
                applyFamilySnapshot(FamilyShellSnapshot.empty())
            } finally {
                if (isCurrentAccount(accountKey)) _isLoading.value = false
            }
        }
    }

    private fun applyFamilySnapshot(snapshot: FamilyShellSnapshot) {
        _hasFamily.value = snapshot.hasFamily
        _familyName.value = snapshot.familyName
        _showManageFamilyActions.value = snapshot.showManageFamilyActions
        _selfProfileId.value = snapshot.selfProfileId
        _memberRole.value = snapshot.memberRole
        _profiles.value = snapshot.profiles
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
            _error.value = userMessageForNetworkFailure(exception)
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

        val previousProfileId = activeProfileManager.currentProfileId.value
            .takeIf { it > ActiveProfileManager.UNSET_PROFILE_ID }
        switchJob?.cancel()
        val generation = ++switchGeneration
        _switchProfileError.value = null
        applyActiveProfileId(accountKey, profileId)
        switchJob = viewModelScope.launch {
            _isSwitchingProfile.value = true
            try {
                val selected = familyProfileRepository.setActiveProfile(profileId)
                if (!isCurrentAccount(accountKey) || generation != switchGeneration) return@launch
                require(selected.profileId == profileId && selected.profileId > 0) {
                    "Active-profile update returned an unexpected profile id."
                }
                applyActiveProfileId(accountKey, selected.profileId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: FamilyApiException) {
                if (!isCurrentAccount(accountKey) || generation != switchGeneration) return@launch
                Timber.w(exception, "Switch profile failed")
                rollbackOptimisticSwitch(accountKey, profileId, previousProfileId)
                _switchProfileError.value = when (exception.statusCode) {
                    403 -> "That profile is not in your family circle."
                    409 -> "That profile is inactive and cannot be selected."
                    else -> exception.message ?: "Could not switch profile. Please try again."
                }
            } catch (exception: Exception) {
                if (!isCurrentAccount(accountKey) || generation != switchGeneration) return@launch
                Timber.w(exception, "Switch profile failed")
                rollbackOptimisticSwitch(accountKey, profileId, previousProfileId)
                _switchProfileError.value =
                    "Could not switch profile. Check your connection and try again."
            } finally {
                if (isCurrentAccount(accountKey) && generation == switchGeneration) {
                    _isSwitchingProfile.value = false
                }
            }
        }
    }

    private fun rollbackOptimisticSwitch(
        accountKey: AuthAccountKey,
        attemptedProfileId: Long,
        previousProfileId: Long?,
    ) {
        if (!activeProfileManager.isCurrent(accountKey, attemptedProfileId)) return
        if (previousProfileId != null && previousProfileId > ActiveProfileManager.UNSET_PROFILE_ID) {
            applyActiveProfileId(accountKey, previousProfileId)
        }
    }

    fun setPendingVerdict(profileId: Long, detail: VerdictDetail) {
        val accountKey = authSessionStore.accountKey.value ?: return
        if (activeProfileManager.isCurrent(accountKey, profileId)) {
            pendingVerdictHolder.set(detail)
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

    fun refreshNotifications() {
        notificationBadge.refreshNotifications()
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
            } catch (exception: FamilyApiException) {
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
            "You're not in a family circle yet. Create one here."

        const val NO_SESSION_FAMILY_MESSAGE =
            "Sign in to create a family circle."
    }
}
