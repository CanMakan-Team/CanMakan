package sg.edu.nus.iss.canmakan.features.product.history

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
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.product.history.data.ScanHistoryRepository
import sg.edu.nus.iss.canmakan.features.product.history.model.ScanHistoryScreenUiState
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScanHistoryViewModel @Inject constructor(
    private val activeProfileManager: ActiveProfileManager,
    private val scanHistoryRepo: ScanHistoryRepository,
    private val authSessionStore: AuthSessionStore,
) : ViewModel() {

    private data class Context(
        val accountKey: AuthAccountKey?,
        val owner: ActiveProfileManager.Selection?,
    )

    private val _scanHistoryUiState = MutableStateFlow(ScanHistoryScreenUiState())
    val scanHistoryUiState: StateFlow<ScanHistoryScreenUiState> = _scanHistoryUiState
    private var loadJob: Job? = null

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
                    val owner = context.owner
                    if (owner == null) {
                        _scanHistoryUiState.value = ScanHistoryScreenUiState(
                            requiresProfileSetup = context.accountKey != null,
                        )
                    } else {
                        loadJob = viewModelScope.launch { loadScanHistoryForProfile(owner) }
                    }
                }
        }
    }

    private suspend fun loadScanHistoryForProfile(owner: ActiveProfileManager.Selection) {
        _scanHistoryUiState.value = ScanHistoryScreenUiState(isLoading = true)
        try {
            val history = scanHistoryRepo.getScanHistoryForProfile(owner.profileId)
            if (!isCurrentOwner(owner)) return
            _scanHistoryUiState.value = ScanHistoryScreenUiState(scanHistory = history)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (!isCurrentOwner(owner)) return
            Timber.e(exception, "Error loading scan history for active profile")
            val message = when (exception) {
                is java.net.SocketTimeoutException ->
                    "Connection timed out. Please check the configured backend connection."
                is java.net.ConnectException ->
                    "Failed to connect to the server. Please check your network."
                else -> "Unable to load scan history. Please try again."
            }
            _scanHistoryUiState.value = ScanHistoryScreenUiState(errorMessage = message)
        }
    }

    private fun isCurrentOwner(owner: ActiveProfileManager.Selection): Boolean =
        authSessionStore.accountKey.value == owner.accountKey &&
            activeProfileManager.isCurrent(owner.accountKey, owner.profileId)
}
