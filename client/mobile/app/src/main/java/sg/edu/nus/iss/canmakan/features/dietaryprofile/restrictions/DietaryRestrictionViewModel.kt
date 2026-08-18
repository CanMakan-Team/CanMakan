package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestrictionSheetUiState
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.shared.util.userMessageForNetworkFailure
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DietaryRestrictionViewModel @Inject constructor(
    private val activeProfileManager: ActiveProfileManager,
    private val dietaryRestrictionRepo: DietaryRestrictionRepository,
    private val authSessionStore: AuthSessionStore,
    private val familyProfileRepository: FamilyProfileRepository,
) : ViewModel() {

    private data class Context(
        val accountKey: AuthAccountKey?,
        val owner: ActiveProfileManager.Selection?,
    )

    private data class EditAuthorization(
        val allow: Boolean,
        val hint: String?,
    )

    private val _uiState = MutableStateFlow(DietaryRestrictionSheetUiState())
    val uiState: StateFlow<DietaryRestrictionSheetUiState> = _uiState

    private var currentOwner: ActiveProfileManager.Selection? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var saveJob: Job? = null
    private var saveGeneration = 0L

    init {
        viewModelScope.launch {
            combine(
                authSessionStore.accountKey,
                activeProfileManager.selection,
            ) { accountKey, selection ->
                Context(
                    accountKey = accountKey,
                    owner = selection?.takeIf { it.accountKey == accountKey },
                )
            }
                .distinctUntilChanged()
                .collect { context ->
                    currentOwner = context.owner
                    loadJob?.cancel()
                    val generation = ++loadGeneration
                    saveGeneration++
                    saveJob?.cancel()
                    _uiState.value = DietaryRestrictionSheetUiState(
                        isLoading = context.owner != null,
                        errorMessage = if (context.accountKey != null && context.owner == null) {
                            PROFILE_SETUP_REQUIRED_MESSAGE
                        } else {
                            null
                        },
                        allowRestrictionEdit = false,
                    )
                    context.owner?.let { owner ->
                        loadJob = viewModelScope.launch { loadForOwner(owner, generation) }
                    }
                }
        }
    }

    private suspend fun loadForOwner(
        owner: ActiveProfileManager.Selection,
        generation: Long,
    ) {
        try {
            val allDietaryRestrictions = dietaryRestrictionRepo.getAllDietaryRestrictions()
            if (!isCurrentLoad(owner, generation)) return

            val savedDietaryRestrictions =
                dietaryRestrictionRepo.getDietaryRestrictionsForProfile(owner.profileId)
            if (!isCurrentLoad(owner, generation)) return

            val authorization = resolveEditAuthorization(owner, generation) ?: return
            if (!isCurrentLoad(owner, generation)) return

            val presentedCatalog = DairyRestrictionPresentation.presentCatalog(allDietaryRestrictions)
            val presentedSelections = DairyRestrictionPresentation.presentSelections(
                catalog = allDietaryRestrictions,
                saved = savedDietaryRestrictions,
            )

            _uiState.value = _uiState.value.copy(
                religiousRestrictions = presentedCatalog.filter { it.category == "RELIGIOUS" },
                allergenRestrictions = presentedCatalog.filter { it.category == "ALLERGEN" },
                dietRestrictions = presentedCatalog.filter { it.category == "DIET" },
                selectedRestrictions = presentedSelections,
                isLoading = false,
                errorMessage = null,
                allowRestrictionEdit = authorization.allow,
                restrictionEditHint = authorization.hint,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (!isCurrentLoad(owner, generation)) return
            Timber.e(exception, "Error loading dietary restrictions")
            val message = userMessageForNetworkFailure(exception)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = message,
                allowRestrictionEdit = false,
            )
        }
    }

    /**
     * Resolves D3 edit permission for the exact account/profile load. Family lookup failures
     * fail closed, while a missing family permits editing the caller's personal profile.
     */
    private suspend fun resolveEditAuthorization(
        owner: ActiveProfileManager.Selection,
        generation: Long,
    ): EditAuthorization? {
        return try {
            val me = familyProfileRepository.getMyFamily()
            if (!isCurrentLoad(owner, generation)) return null
            if (me == null) return EditAuthorization(allow = true, hint = null)

            val allow = RestrictionEditAuthorization.mayEditRestrictions(
                profileId = owner.profileId,
                hasFamily = true,
                me = me,
            )

            EditAuthorization(
                allow = allow,
                hint = if (allow) null else RestrictionEditAuthorization.READ_ONLY_HINT,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (!isCurrentLoad(owner, generation)) return null
            Timber.e(exception, "Error resolving restriction edit permission for profile ${owner.profileId}")
            EditAuthorization(
                allow = false,
                hint = RestrictionEditAuthorization.READ_ONLY_HINT,
            )
        }
    }

    /** Explicit retry for the currently authenticated account and active profile. */
    suspend fun loadDietaryRestrictions() {
        val owner = currentValidOwner()
        if (owner == null) {
            _uiState.value = DietaryRestrictionSheetUiState(
                errorMessage = PROFILE_SETUP_REQUIRED_MESSAGE,
                allowRestrictionEdit = false,
            )
            return
        }

        loadJob?.cancel()
        val generation = ++loadGeneration
        _uiState.value = DietaryRestrictionSheetUiState(
            isLoading = true,
            allowRestrictionEdit = false,
        )
        loadForOwner(owner, generation)
    }

    fun selectReligiousRestriction(restrictionId: Long) {
        if (currentValidOwner() == null) {
            _uiState.value = _uiState.value.copy(errorMessage = PROFILE_SETUP_REQUIRED_MESSAGE)
            return
        }
        if (_uiState.value.allowRestrictionEdit != true) return

        val currentSelections = _uiState.value.selectedRestrictions.toMutableMap()
        val isAlreadySelected = currentSelections.containsKey(restrictionId)
        _uiState.value.religiousRestrictions
            .map { it.id }
            .forEach(currentSelections::remove)
        if (!isAlreadySelected) currentSelections[restrictionId] = "STRICT_AVOID"
        _uiState.value = _uiState.value.copy(selectedRestrictions = currentSelections)
    }

    fun toggleDietaryRestriction(restrictionId: Long) {
        if (currentValidOwner() == null) {
            _uiState.value = _uiState.value.copy(errorMessage = PROFILE_SETUP_REQUIRED_MESSAGE)
            return
        }
        if (_uiState.value.allowRestrictionEdit != true) return

        val currentSelections = _uiState.value.selectedRestrictions.toMutableMap()
        if (currentSelections.containsKey(restrictionId)) {
            currentSelections.remove(restrictionId)
        } else {
            currentSelections[restrictionId] = "STRICT_AVOID"
        }
        _uiState.value = _uiState.value.copy(selectedRestrictions = currentSelections)
    }

    fun onSave(onSuccess: () -> Unit = {}) {
        val owner = currentValidOwner()
        if (owner == null) {
            _uiState.value = _uiState.value.copy(errorMessage = PROFILE_SETUP_REQUIRED_MESSAGE)
            return
        }
        if (_uiState.value.allowRestrictionEdit != true) {
            _uiState.value = _uiState.value.copy(
                errorMessage = RestrictionEditAuthorization.READ_ONLY_HINT,
            )
            return
        }

        val selections = _uiState.value.selectedRestrictions.toMap()
        val catalogForSave = _uiState.value.religiousRestrictions +
            _uiState.value.allergenRestrictions +
            _uiState.value.dietRestrictions
        // Alias ids are already absent from UI state; keep helper for safety if catalog grows.
        val persistedSelections = DairyRestrictionPresentation.selectionsForSave(
            catalog = catalogForSave,
            selections = selections,
        )
        saveJob?.cancel()
        val generation = ++saveGeneration
        saveJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                if (!isCurrentOwner(owner) || generation != saveGeneration) return@launch
                val saved = dietaryRestrictionRepo.saveDietaryRestrictionSelections(
                    profileId = owner.profileId,
                    selections = persistedSelections,
                )
                if (!isCurrentOwner(owner) || generation != saveGeneration) return@launch
                if (saved) {
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Unable to save dietary restrictions. Please try again.",
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!isCurrentOwner(owner) || generation != saveGeneration) return@launch
                Timber.e(exception, "Error saving dietary restrictions")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Unable to save dietary restrictions. Please try again.",
                )
            } finally {
                if (isCurrentOwner(owner) && generation == saveGeneration) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    private fun currentValidOwner(): ActiveProfileManager.Selection? {
        val owner = currentOwner ?: return null
        return owner.takeIf(::isCurrentOwner)
    }

    private fun isCurrentLoad(
        owner: ActiveProfileManager.Selection,
        generation: Long,
    ): Boolean = generation == loadGeneration && isCurrentOwner(owner)

    private fun isCurrentOwner(owner: ActiveProfileManager.Selection): Boolean =
        authSessionStore.accountKey.value == owner.accountKey &&
            activeProfileManager.isCurrent(owner.accountKey, owner.profileId)

    private companion object {
        const val PROFILE_SETUP_REQUIRED_MESSAGE =
            "Complete profile setup before saving dietary restrictions."
    }
}
