package sg.edu.nus.iss.canmakan.features.product.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.product.recommendation.data.RecommendationHistoryRepository
import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryScreenUiState
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RecommendationHistoryViewModel @Inject constructor(
    private val activeProfileManager: ActiveProfileManager,
    private val recommendationHistoryRepository: RecommendationHistoryRepository,
    private val authSessionStore: AuthSessionStore,
) : ViewModel() {

    private data class Context(
        val accountKey: AuthAccountKey?,
        val owner: ActiveProfileManager.Selection?,
    )

    private val _uiState = MutableStateFlow(RecommendationHistoryScreenUiState())
    val uiState: StateFlow<RecommendationHistoryScreenUiState> = _uiState

    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init {
        viewModelScope.launch {
            combine(
                authSessionStore.accountKey,
                activeProfileManager.selection,
            ) { accountKey, selection ->
                Context(accountKey, selection?.takeIf { it.accountKey == accountKey })
            }
                .distinctUntilChanged()
                .collect { context ->
                    loadJob?.cancel()
                    val generation = ++loadGeneration
                    val owner = context.owner
                    if (owner == null) {
                        _uiState.value = RecommendationHistoryScreenUiState(
                            requiresProfileSetup = context.accountKey != null,
                        )
                    } else {
                        loadJob = viewModelScope.launch { loadForProfile(owner, generation) }
                    }
                }
        }
    }

    private suspend fun loadForProfile(
        owner: ActiveProfileManager.Selection,
        generation: Long,
    ) {
        _uiState.value = RecommendationHistoryScreenUiState(isLoading = true)
        try {
            val entries = recommendationHistoryRepository
                .getRecommendationHistoryForProfile(owner.profileId)
            if (!isCurrentLoad(owner, generation)) return
            _uiState.value = RecommendationHistoryScreenUiState(entries = entries)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (!isCurrentLoad(owner, generation)) return
            Timber.e(exception, "Error loading recommendation history for profile ${owner.profileId}")
            val message = when (exception) {
                is java.net.SocketTimeoutException ->
                    "Connection timed out. Please check if the backend server is running."
                is java.net.ConnectException ->
                    "Failed to connect to the server. Please check your network."
                else -> "Unable to load recommendation history. Please try again."
            }
            _uiState.value = RecommendationHistoryScreenUiState(errorMessage = message)
        }
    }

    private fun isCurrentLoad(
        owner: ActiveProfileManager.Selection,
        generation: Long,
    ): Boolean =
        generation == loadGeneration &&
            authSessionStore.accountKey.value == owner.accountKey &&
            activeProfileManager.isCurrent(owner.accountKey, owner.profileId)
}
