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
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.DairyRestrictionPresentation
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.ProfileRestrictionSeverity
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.SelfProfileRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.SelfProfileSetupResult

data class AuthenticatedDietaryOnboardingUiState(
    val profileName: String = "",
    val profileNameEditable: Boolean = false,
    val restrictions: List<DietaryRestriction> = emptyList(),
    val selections: Map<Long, ProfileRestrictionSeverity> = emptyMap(),
    val isLoadingCatalog: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val resolved: Boolean = false,
)

/**
 * Drives optional SELF-profile creation after account-only registration and normal login.
 * Catalog presentation is shared with the edit flow, while profile creation remains a
 * separate authenticated transaction. Every action is bound to the current account.
 */
@HiltViewModel
class AuthenticatedDietaryOnboardingViewModel @Inject constructor(
    private val authSessionStore: AuthSessionStore,
    private val pendingOnboardingStore: PendingOnboardingStore,
    private val dietaryRestrictionRepository: DietaryRestrictionRepository,
    private val selfProfileRepository: SelfProfileRepository,
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
        _uiState.value = AuthenticatedDietaryOnboardingUiState(
            profileName = pending.accountName.orEmpty(),
            profileNameEditable = pending.accountName.isNullOrBlank(),
        )
        loadRestrictions(user)
    }

    fun updateProfileName(profileName: String) {
        val state = _uiState.value
        if (!state.profileNameEditable || state.isSubmitting || state.resolved) return
        _uiState.value = state.copy(profileName = profileName, errorMessage = null)
    }

    fun retryCatalog() {
        if (!_uiState.value.isLoadingCatalog && !_uiState.value.isSubmitting) {
            val user = currentUser() ?: return
            if (isCurrentSetup(user)) {
                loadRestrictions(user)
            }
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

    /** Creates the caller's SELF profile through the authenticated setup endpoint. */
    fun saveRestrictions() {
        val state = _uiState.value
        if (state.isSubmitting || state.resolved) return
        val initiatingUser = currentUser()
        if (initiatingUser == null || !isCurrentSetup(initiatingUser)) {
            _uiState.value = state.copy(errorMessage = SESSION_REQUIRED_MESSAGE)
            return
        }
        if (state.selections.isEmpty()) {
            _uiState.value = state.copy(
                errorMessage = "Select at least one dietary restriction or set up your profile later.",
            )
            return
        }
        val profileName = state.profileName.trim()
        if (profileName.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Enter a profile name.")
            return
        }
        if (profileName.length > MAX_PROFILE_NAME_LENGTH) {
            _uiState.value = state.copy(
                errorMessage = "Profile name must not exceed 100 characters.",
            )
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        setupJob = viewModelScope.launch {
            val result = try {
                selfProfileRepository.createSelfProfile(
                    profileName = profileName,
                    restrictions = state.selections,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                SelfProfileSetupResult.Failure(
                    "Unable to create your dietary profile. Check your connection and try again.",
                )
            }
            if (!isCurrentSetup(initiatingUser)) return@launch
            when (result) {
                is SelfProfileSetupResult.Created -> {
                    val profileId = result.profile.profileId
                    if (profileId == null || profileId <= 0) {
                        showError("Dietary profile setup returned an invalid response. Try again.")
                    } else {
                        completeWithProfile(profileId, initiatingUser)
                    }
                }
                is SelfProfileSetupResult.InvalidRequest -> showError(result.message)
                SelfProfileSetupResult.Unauthenticated -> showError(SESSION_REQUIRED_MESSAGE)
                SelfProfileSetupResult.Forbidden -> showError(
                    "This account cannot create a dietary profile.",
                )
                SelfProfileSetupResult.AlreadyExists -> showError(
                    "A dietary profile already exists for this account.",
                )
                is SelfProfileSetupResult.Failure -> showError(result.message)
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
                val catalog = dietaryRestrictionRepository.getAllDietaryRestrictions()
                if (!isCurrentSetup(initiatingUser)) return@launch
                _uiState.value = _uiState.value.copy(
                    restrictions = DairyRestrictionPresentation.presentCatalog(catalog),
                )
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

    private fun completeWithProfile(profileId: Long, initiatingUser: AuthenticatedUser) {
        if (profileId <= 0) {
            showErrorIfCurrent(initiatingUser, SESSION_REQUIRED_MESSAGE)
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

    /**
     * Every mutating action (toggle, save, defer) is gated on this: the caller must still be
     * the exact authenticated account this screen was bound to, with its onboarding intent
     * still current. A session change (sign-out, sign-in as someone else) fails this closed.
     */
    private fun isCurrentSetup(user: AuthenticatedUser): Boolean {
        val pending = boundPending ?: return false
        return isCurrentAccount(user) && pendingOnboardingStore.isCurrent(pending)
    }

    companion object {
        const val SESSION_REQUIRED_MESSAGE =
            "Your authenticated session is required before dietary profile setup."
        const val SESSION_CHANGED_MESSAGE =
            "Dietary profile setup stopped because the authenticated account changed."
        private const val RELIGIOUS_CATEGORY = "RELIGIOUS"
        private const val MAX_PROFILE_NAME_LENGTH = 100
    }
}
