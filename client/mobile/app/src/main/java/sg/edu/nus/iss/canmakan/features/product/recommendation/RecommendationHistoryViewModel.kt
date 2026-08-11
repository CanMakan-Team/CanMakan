package sg.edu.nus.iss.canmakan.features.product.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.product.recommendation.data.RecommendationHistoryRepository
import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryScreenUiState
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RecommendationHistoryViewModel @Inject constructor(
    private val activeProfileManager: ActiveProfileManager,
    private val recommendationHistoryRepository: RecommendationHistoryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecommendationHistoryScreenUiState())
    val uiState: StateFlow<RecommendationHistoryScreenUiState> = _uiState

    init {
        viewModelScope.launch {
            loadForProfile(activeProfileManager.currentProfileId.value)

            activeProfileManager.currentProfileId.collect { profileId ->
                loadForProfile(profileId)
            }
        }
    }

    private suspend fun loadForProfile(profileId: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        try {
            val entries = recommendationHistoryRepository.getRecommendationHistoryForProfile(profileId)
            _uiState.value = _uiState.value.copy(entries = entries)
        } catch (e: Exception) {
            Timber.e(e, "Error loading recommendation history for profile $profileId")
            val message = when (e) {
                is java.net.SocketTimeoutException ->
                    "Connection timed out. Please check if the backend server is running."
                is java.net.ConnectException ->
                    "Failed to connect to the server. Please check your network."
                else -> "Unable to load recommendation history. Please try again."
            }
            _uiState.value = _uiState.value.copy(errorMessage = message, entries = emptyList())
        } finally {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}
