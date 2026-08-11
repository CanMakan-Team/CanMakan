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
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DietaryRestrictionViewModel @Inject constructor(
    private val activeProfileManager: ActiveProfileManager,
    private val dietaryRestrictionRepo: DietaryRestrictionRepository,
    private val authSessionStore: AuthSessionStore,
) : ViewModel() {

    private data class Context(
        val accountKey: AuthAccountKey?,
        val owner: ActiveProfileManager.Selection?,
    )

    private val _uiState = MutableStateFlow(DietaryRestrictionSheetUiState())
    val uiState: StateFlow<DietaryRestrictionSheetUiState> = _uiState

    private var currentOwner: ActiveProfileManager.Selection? = null
    private var loadJob: Job? = null
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
                    saveGeneration++
                    saveJob?.cancel()
                    _uiState.value = DietaryRestrictionSheetUiState(
                        isLoading = context.owner != null,
                        errorMessage = if (context.accountKey != null && context.owner == null) {
                            PROFILE_SETUP_REQUIRED_MESSAGE
                        } else {
                            null
                        },
                    )
                    context.owner?.let { owner ->
                        loadJob = viewModelScope.launch { loadForOwner(owner) }
                    }
                }
        }
    }

    private suspend fun loadForOwner(owner: ActiveProfileManager.Selection) {
        try {
            val allDietaryRestrictions = dietaryRestrictionRepo.getAllDietaryRestrictions()
            if (!isCurrentOwner(owner)) return
            val savedDietaryRestrictions =
                dietaryRestrictionRepo.getDietaryRestrictionsForProfile(owner.profileId)
            if (!isCurrentOwner(owner)) return

            _uiState.value = _uiState.value.copy(
                religiousRestrictions = allDietaryRestrictions.filter { it.category == "RELIGIOUS" },
                allergenRestrictions = allDietaryRestrictions.filter { it.category == "ALLERGEN" },
                dietRestrictions = allDietaryRestrictions.filter { it.category == "DIET" },
                selectedRestrictions = savedDietaryRestrictions,
                isLoading = false,
                errorMessage = null,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (!isCurrentOwner(owner)) return
            Timber.e(exception, "Error loading dietary restrictions")
            val message = when (exception) {
                is java.net.SocketTimeoutException ->
                    "Connection timed out. Please check the configured backend connection."
                is java.net.ConnectException ->
                    "Failed to connect to the server. Please check your network."
                else -> "Unable to load dietary restrictions. Please try again."
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = message,
            )
        }
    }

    /** Explicit retry for the currently authenticated account and active profile. */
    suspend fun loadDietaryRestrictions() {
        val owner = currentValidOwner()
        if (owner == null) {
            _uiState.value = DietaryRestrictionSheetUiState(
                errorMessage = PROFILE_SETUP_REQUIRED_MESSAGE,
            )
            return
        }
        _uiState.value = DietaryRestrictionSheetUiState(isLoading = true)
        loadForOwner(owner)
    }

    fun selectReligiousRestriction(restrictionId: Long) {
        if (currentValidOwner() == null) {
            _uiState.value = _uiState.value.copy(errorMessage = PROFILE_SETUP_REQUIRED_MESSAGE)
            return
        }
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
        val selections = _uiState.value.selectedRestrictions.toMap()
        saveJob?.cancel()
        val generation = ++saveGeneration
        saveJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                if (!isCurrentOwner(owner) || generation != saveGeneration) return@launch
                val saved = dietaryRestrictionRepo.saveDietaryRestrictionSelections(
                    profileId = owner.profileId,
                    selections = selections,
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

    private fun isCurrentOwner(owner: ActiveProfileManager.Selection): Boolean =
        authSessionStore.accountKey.value == owner.accountKey &&
            activeProfileManager.isCurrent(owner.accountKey, owner.profileId)

    private companion object {
        const val PROFILE_SETUP_REQUIRED_MESSAGE =
            "Complete profile setup before saving dietary restrictions."
    }
}
