package sg.edu.nus.iss.canmakan.features.dietaryprofile.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.onboarding.PendingOnboardingStore
import sg.edu.nus.iss.canmakan.features.auth.onboarding.PendingDietaryOnboarding
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.ProfileRestrictionSeverity
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.ExistingSelfProfileResolver
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.SelfProfileRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.SelfProfileSetupResult
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager

data class AuthenticatedDietaryOnboardingUiState(
    val profileName: String = "",
    val restrictions: List<DietaryRestriction> = emptyList(),
    val selections: Map<Long, ProfileRestrictionSeverity> = emptyMap(),
    val isLoadingCatalog: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val resolved: Boolean = false,
)

@HiltViewModel
class AuthenticatedDietaryOnboardingViewModel @Inject constructor(
    private val authSessionStore: AuthSessionStore,
    private val pendingOnboardingStore: PendingOnboardingStore,
    private val dietaryRestrictionRepository: DietaryRestrictionRepository,
    private val selfProfileRepository: SelfProfileRepository,
    private val existingSelfProfileResolver: ExistingSelfProfileResolver,
    private val activeProfileManager: ActiveProfileManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthenticatedDietaryOnboardingUiState(),
    )
    val uiState: StateFlow<AuthenticatedDietaryOnboardingUiState> = _uiState.asStateFlow()

    private var setupJob: Job? = null
    private var catalogJob: Job? = null
    private var boundAccountKey: AuthAccountKey? = null
    private var boundAccountEmail: String? = null
    private var boundPending: PendingDietaryOnboarding? = null

    init {
        viewModelScope.launch {
            authSessionStore.accountKey.collect { accountKey ->
                val boundKey = boundAccountKey
                if (boundKey != null && accountKey != boundKey) {
                    setupJob?.cancel()
                    catalogJob?.cancel()
                    boundAccountEmail?.let(pendingOnboardingStore::clearForAccount)
                    setupJob = null
                    catalogJob = null
                    boundAccountKey = null
                    boundAccountEmail = null
                    boundPending = null
                    _uiState.value = AuthenticatedDietaryOnboardingUiState(
                        errorMessage = SESSION_CHANGED_MESSAGE,
                    )
                }
            }
        }
    }

    fun beginPendingSetup() {
        val user = currentUser()
        if (user == null) {
            _uiState.value = AuthenticatedDietaryOnboardingUiState(
                errorMessage = SESSION_REQUIRED_MESSAGE,
            )
            return
        }
        val pending = pendingOnboardingStore.peekForAccount(user.email)
        if (pending == null) {
            _uiState.value = AuthenticatedDietaryOnboardingUiState(resolved = true)
            return
        }
        bindTo(user, pending)
        _uiState.value = AuthenticatedDietaryOnboardingUiState()
        loadRestrictions(user)
    }

    fun retryCatalog() {
        if (!_uiState.value.isLoadingCatalog && !_uiState.value.isSubmitting) {
            val user = currentUser() ?: return
            if (isCurrentSetup(user)) {
                loadRestrictions(user)
            }
        }
    }

    fun updateProfileName(profileName: String) {
        val state = _uiState.value
        if (!state.isSubmitting && !state.resolved) {
            _uiState.value = state.copy(profileName = profileName, errorMessage = null)
        }
    }

    fun toggleRestriction(restrictionId: Long) {
        val state = _uiState.value
        if (state.isSubmitting || state.resolved) return
        val restriction = state.restrictions.firstOrNull { it.id == restrictionId } ?: return
        val selections = state.selections.toMutableMap()
        if (selections.remove(restrictionId) == null) {
            if (restriction.category == RELIGIOUS_CATEGORY) {
                state.restrictions
                    .filter { it.category == RELIGIOUS_CATEGORY }
                    .forEach { selections.remove(it.id) }
            }
            selections[restrictionId] = ProfileRestrictionSeverity.STRICT_AVOID
        }
        _uiState.value = state.copy(selections = selections, errorMessage = null)
    }

    fun setSeverity(restrictionId: Long, severity: ProfileRestrictionSeverity) {
        val state = _uiState.value
        if (restrictionId !in state.selections || state.isSubmitting || state.resolved) return
        _uiState.value = state.copy(
            selections = state.selections + (restrictionId to severity),
            errorMessage = null,
        )
    }

    fun createProfile() {
        val state = _uiState.value
        if (state.isSubmitting || state.resolved) return
        val initiatingUser = currentUser()
        if (initiatingUser == null || !isCurrentSetup(initiatingUser)) {
            _uiState.value = state.copy(errorMessage = SESSION_REQUIRED_MESSAGE)
            return
        }
        if (state.profileName.isBlank()) {
            _uiState.value = state.copy(errorMessage = "A profile name is required.")
            return
        }
        if (state.profileName.trim().length > MAX_PROFILE_NAME_LENGTH) {
            _uiState.value = state.copy(
                errorMessage = "Profile name must not exceed 100 characters.",
            )
            return
        }
        if (state.selections.isEmpty()) {
            _uiState.value = state.copy(
                errorMessage = "Select at least one dietary restriction or set up your profile later.",
            )
            return
        }

        val profileName = state.profileName.trim()
        val selections = state.selections.toMap()
        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        setupJob = viewModelScope.launch {
            when (val result = selfProfileRepository.createSelfProfile(
                profileName = profileName,
                restrictions = selections,
            )) {
                is SelfProfileSetupResult.Created -> {
                    val profileId = result.profile.profileId
                    if (profileId == null) {
                        showErrorIfCurrent(initiatingUser, INVALID_PROFILE_RESPONSE_MESSAGE)
                    } else {
                        completeWithProfile(profileId, initiatingUser)
                    }
                }
                is SelfProfileSetupResult.InvalidRequest ->
                    showErrorIfCurrent(initiatingUser, result.message)
                SelfProfileSetupResult.Unauthenticated ->
                    showErrorIfCurrent(initiatingUser, SESSION_REQUIRED_MESSAGE)
                SelfProfileSetupResult.Forbidden -> showErrorIfCurrent(
                    initiatingUser,
                    "This account cannot create a mobile dietary profile.",
                )
                SelfProfileSetupResult.AlreadyExists -> resolveExistingProfile(
                    initiatingUser = initiatingUser,
                    selections = selections,
                )
                is SelfProfileSetupResult.Failure ->
                    showErrorIfCurrent(initiatingUser, result.message)
            }
        }
    }

    fun deferSetup() {
        if (_uiState.value.isSubmitting) return
        val user = currentUser() ?: return
        if (!isCurrentSetup(user)) return
        catalogJob?.cancel()
        boundPending?.let(pendingOnboardingStore::clearIfCurrent)
        _uiState.value = _uiState.value.copy(resolved = true, errorMessage = null)
    }

    private fun loadRestrictions(initiatingUser: AuthenticatedUser) {
        if (pendingOnboardingStore.peekForAccount(initiatingUser.email) == null) {
            _uiState.value = _uiState.value.copy(resolved = true)
            return
        }
        if (!isCurrentSetup(initiatingUser)) {
            _uiState.value = _uiState.value.copy(errorMessage = SESSION_REQUIRED_MESSAGE)
            return
        }
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCatalog = true, errorMessage = null)
            try {
                val restrictions = dietaryRestrictionRepository.getAllDietaryRestrictions()
                if (isCurrentSetup(initiatingUser)) {
                    _uiState.value = _uiState.value.copy(restrictions = restrictions)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (isCurrentSetup(initiatingUser)) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Unable to load dietary options. Check your connection and try again.",
                    )
                }
            } finally {
                if (isCurrentSetup(initiatingUser)) {
                    _uiState.value = _uiState.value.copy(isLoadingCatalog = false)
                }
            }
        }
    }

    private suspend fun resolveExistingProfile(
        initiatingUser: AuthenticatedUser,
        selections: Map<Long, ProfileRestrictionSeverity>,
    ) {
        val profileId = try {
            existingSelfProfileResolver.resolveActiveSelfProfileId()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            showErrorIfCurrent(
                initiatingUser,
                "A SELF profile already exists, but its active profile could not be resolved. " +
                    "Continue later rather than creating another profile.",
            )
            return
        }
        if (profileId <= 0 || !isCurrentSetup(initiatingUser)) return

        val restrictionsSaved = try {
            dietaryRestrictionRepository.saveDietaryRestrictionSelections(
                profileId = profileId,
                selections = selections.mapValues { it.value.name },
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            false
        }
        if (!isCurrentSetup(initiatingUser)) return
        if (!restrictionsSaved) {
            showError(
                "Your existing SELF profile was found, but its dietary restrictions could not " +
                    "be saved. Retry or continue later.",
            )
            return
        }
        completeWithProfile(profileId, initiatingUser)
    }

    private fun completeWithProfile(profileId: Long, initiatingUser: AuthenticatedUser) {
        if (profileId <= 0) {
            showErrorIfCurrent(initiatingUser, INVALID_PROFILE_RESPONSE_MESSAGE)
            return
        }
        if (!isCurrentSetup(initiatingUser)) return
        val accountKey = boundAccountKey ?: return
        activeProfileManager.switchProfile(accountKey, profileId)
        if (!isCurrentSetup(initiatingUser)) {
            activeProfileManager.resetForOwner(accountKey)
            return
        }
        boundPending?.let(pendingOnboardingStore::clearIfCurrent)
        _uiState.value = _uiState.value.copy(
            isSubmitting = false,
            errorMessage = null,
            resolved = true,
        )
    }

    private fun showErrorIfCurrent(initiatingUser: AuthenticatedUser, message: String) {
        if (isCurrentSetup(initiatingUser)) showError(message)
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(
            isSubmitting = false,
            errorMessage = message,
        )
    }

    private fun bindTo(user: AuthenticatedUser, pending: PendingDietaryOnboarding) {
        val accountKey = authSessionStore.accountKey.value
            ?.takeIf { it.userId == user.userId }
            ?: return
        if (boundAccountKey != null && boundAccountKey != accountKey) {
            setupJob?.cancel()
            catalogJob?.cancel()
            setupJob = null
            catalogJob = null
            boundAccountEmail?.let(pendingOnboardingStore::clearForAccount)
        }
        boundAccountKey = accountKey
        boundAccountEmail = user.email
        boundPending = pending
    }

    private fun currentUser(): AuthenticatedUser? = authSessionStore.authenticatedUser.value

    private fun isCurrentAccount(user: AuthenticatedUser): Boolean {
        val accountKey = boundAccountKey ?: return false
        return accountKey.userId == user.userId &&
            authSessionStore.accountKey.value == accountKey &&
            currentUser()?.userId == user.userId
    }

    private fun isCurrentSetup(user: AuthenticatedUser): Boolean {
        val pending = boundPending ?: return false
        return isCurrentAccount(user) && pendingOnboardingStore.isCurrent(pending)
    }

    companion object {
        const val SESSION_REQUIRED_MESSAGE =
            "Your authenticated session is required before dietary profile setup."
        const val SESSION_CHANGED_MESSAGE =
            "Dietary profile setup stopped because the authenticated account changed."
        private const val INVALID_PROFILE_RESPONSE_MESSAGE =
            "Dietary profile setup returned an invalid profile. Try again later."
        private const val MAX_PROFILE_NAME_LENGTH = 100
        private const val RELIGIOUS_CATEGORY = "RELIGIOUS"
    }
}
