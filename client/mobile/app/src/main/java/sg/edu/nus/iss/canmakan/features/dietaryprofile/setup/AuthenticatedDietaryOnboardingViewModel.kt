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
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.ExistingSelfProfileResolver
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager

data class AuthenticatedDietaryOnboardingUiState(
    val restrictions: List<DietaryRestriction> = emptyList(),
    val selections: Map<Long, ProfileRestrictionSeverity> = emptyMap(),
    val isLoadingCatalog: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val resolved: Boolean = false,
)

/**
 * Drives the post-registration dietary setup screen. Registration always creates the
 * account's linked SELF profile up front, so this screen only ever loads the restriction
 * catalog and saves selections onto that existing profile — the same
 * [DietaryRestrictionRepository] calls
 * [sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.ui.DietaryRestrictionSheet]
 * uses to view/edit an existing profile's restrictions. Every action still requires the
 * caller to be the currently bound, authenticated account (see [isCurrentSetup]) — reusing
 * the repository does not relax that.
 */
@HiltViewModel
class AuthenticatedDietaryOnboardingViewModel @Inject constructor(
    private val authSessionStore: AuthSessionStore,
    private val pendingOnboardingStore: PendingOnboardingStore,
    private val dietaryRestrictionRepository: DietaryRestrictionRepository,
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
    private var resolvedProfileId: Long? = null

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
                    resolvedProfileId = null
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
        resolvedProfileId = null
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

    /**
     * Saves the caller's selections onto the SELF profile registration already created.
     * Requires the caller to still be the authenticated, currently bound account
     * ([isCurrentSetup]) — the same guard every other mutating action here uses.
     */
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
        val profileId = resolvedProfileId
        if (profileId == null || profileId <= 0) {
            _uiState.value = state.copy(errorMessage = SESSION_REQUIRED_MESSAGE)
            return
        }

        val selections = state.selections.mapValues { it.value.name }
        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        setupJob = viewModelScope.launch {
            val saved = try {
                dietaryRestrictionRepository.saveDietaryRestrictionSelections(
                    profileId = profileId,
                    selections = selections,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                false
            }
            if (!isCurrentSetup(initiatingUser)) return@launch
            if (!saved) {
                showError("Unable to save your dietary restrictions. Check your connection and try again.")
                return@launch
            }
            completeWithProfile(profileId, initiatingUser)
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
                // Same profile lookup DietaryRestrictionSheet relies on: registration
                // already created this account's SELF profile, so it always resolves.
                val profileId = existingSelfProfileResolver.resolveActiveSelfProfileId()
                if (!isCurrentSetup(initiatingUser)) return@launch

                val catalog = dietaryRestrictionRepository.getAllDietaryRestrictions()
                if (!isCurrentSetup(initiatingUser)) return@launch

                val savedSelections =
                    dietaryRestrictionRepository.getDietaryRestrictionsForProfile(profileId)
                if (!isCurrentSetup(initiatingUser)) return@launch

                resolvedProfileId = profileId
                _uiState.value = _uiState.value.copy(
                    restrictions = DairyRestrictionPresentation.presentCatalog(catalog),
                    selections = toSelfSetupSelections(
                        DairyRestrictionPresentation.presentSelections(catalog, savedSelections),
                    ),
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

    /** Self-setup only supports STRICT_AVOID/INTOLERANCE; drop anything else defensively. */
    private fun toSelfSetupSelections(saved: Map<Long, String>): Map<Long, ProfileRestrictionSeverity> =
        saved.mapNotNull { (restrictionId, severity) ->
            runCatching { ProfileRestrictionSeverity.valueOf(severity) }
                .getOrNull()
                ?.let { restrictionId to it }
        }.toMap()

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
    }
}
