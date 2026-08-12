package sg.edu.nus.iss.canmakan.features.product.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.product.history.data.ScanHistoryRepository
import sg.edu.nus.iss.canmakan.features.product.history.model.ScanHistoryScreenUiState
import sg.edu.nus.iss.canmakan.features.product.model.AlternativeProduct
import sg.edu.nus.iss.canmakan.features.product.recommendation.data.RecommendationHistoryRepository
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScanHistoryViewModel @Inject constructor(
    private val activeProfileManager: ActiveProfileManager,
    private val scanHistoryRepo: ScanHistoryRepository,
    private val recommendationHistoryRepository: RecommendationHistoryRepository
) : ViewModel() {
    private val _scanHistoryUiState = MutableStateFlow(ScanHistoryScreenUiState())
    val scanHistoryUiState: StateFlow<ScanHistoryScreenUiState> = _scanHistoryUiState

    init {
        viewModelScope.launch {
            loadScanHistoryForProfile(activeProfileManager.currentProfileId.value)

            activeProfileManager.currentProfileId.collect { profileId ->
                loadScanHistoryForProfile(profileId)
            }
        }
    }

    private suspend fun loadScanHistoryForProfile(profileId: Long) {
        _scanHistoryUiState.value = _scanHistoryUiState.value.copy(isLoading = true, errorMessage = null)

        try {
            val history = scanHistoryRepo.getScanHistoryForProfile(profileId)
            val alternativesByScanId = loadAlternativesByScanId(profileId)
            _scanHistoryUiState.value = _scanHistoryUiState.value.copy(
                scanHistory = history,
                alternativesByScanId = alternativesByScanId
            )
        } catch (e: Exception) {
            Timber.e(e, "Error loading scan history for profile $profileId")
            val message = when (e) {
                is java.net.SocketTimeoutException -> "Connection timed out. Please check if the backend server is running at ${sg.edu.nus.iss.canmakan.BuildConfig.BASE_URL ?: "the configured API URL"}"
                is java.net.ConnectException -> "Failed to connect to the server. Please check your network."
                else -> "Unable to load scan history. Please try again."
            }
            _scanHistoryUiState.value = _scanHistoryUiState.value.copy(
                errorMessage = message,
                alternativesByScanId = emptyMap()
            )
        } finally {
            _scanHistoryUiState.value = _scanHistoryUiState.value.copy(isLoading = false)
        }
    }

    private suspend fun loadAlternativesByScanId(profileId: Long): Map<Long, List<AlternativeProduct>> {
        return try {
            recommendationHistoryRepository.getRecommendationHistoryForProfile(profileId)
                .mapNotNull { entry ->
                    entry.scanId?.let { scanId ->
                        scanId to entry.toAlternativeProducts()
                    }
                }
                .toMap()
        } catch (e: Exception) {
            Timber.e(e, "Error loading recommendation history for profile $profileId")
            emptyMap()
        }
    }
}
