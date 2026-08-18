package sg.edu.nus.iss.canmakan.features.product.scan

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
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import sg.edu.nus.iss.canmakan.features.product.scan.data.BarcodeValidation
import sg.edu.nus.iss.canmakan.features.product.scan.data.ScanAlternatives
import sg.edu.nus.iss.canmakan.features.product.scan.data.ScanAssessment
import sg.edu.nus.iss.canmakan.features.product.scan.data.ScanRepository
import javax.inject.Inject

enum class ScanProcessState {
    IDLE,
    VALIDATING,
    ASSESSING,
    FETCHING_ALTERNATIVES,
    SUCCESS,
    INVALID,
    ERROR
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val authSessionStore: AuthSessionStore,
    private val activeProfileManager: ActiveProfileManager,
) : ViewModel() {
    private val _processState = MutableStateFlow(ScanProcessState.IDLE)
    val processState: StateFlow<ScanProcessState> = _processState.asStateFlow()

    private val _verdictDetail = MutableStateFlow<VerdictDetail?>(null)
    val verdictDetail: StateFlow<VerdictDetail?> = _verdictDetail.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var scanJob: Job? = null
    private var scanOwner: ActiveProfileManager.Selection? = null
    private var scanGeneration = 0L

    init {
        viewModelScope.launch {
            combine(
                authSessionStore.accountKey,
                activeProfileManager.selection,
            ) { accountKey, selection ->
                selection?.takeIf { it.accountKey == accountKey }
            }
                .distinctUntilChanged()
                .collect { owner ->
                    val operationOwner = scanOwner
                    if (operationOwner != null && operationOwner != owner) resetState()
                }
        }
    }

    fun processBarcode(barcode: String, profileId: Long) {
        val accountKey = authSessionStore.accountKey.value
        val owner = activeProfileManager.selection.value
            ?.takeIf { it.accountKey == accountKey && it.profileId == profileId }
        if (profileId <= 0 || owner == null) {
            invalidateScan()
            _processState.value = ScanProcessState.ERROR
            _verdictDetail.value = null
            _errorMessage.value = PROFILE_SETUP_REQUIRED_MESSAGE
            return
        }

        scanJob?.cancel()
        val generation = ++scanGeneration
        scanOwner = owner
        _processState.value = ScanProcessState.VALIDATING
        _verdictDetail.value = null
        _errorMessage.value = null

        scanJob = viewModelScope.launch {
            try {
                when (val validation = scanRepository.validateBarcode(barcode)) {
                    BarcodeValidation.Failed -> {
                        if (!isCurrentOperation(owner, generation)) return@launch
                        _processState.value = ScanProcessState.ERROR
                        _errorMessage.value = "Product not found or network error"
                        return@launch
                    }
                    is BarcodeValidation.Invalid -> {
                        if (!isCurrentOperation(owner, generation)) return@launch
                        _processState.value = ScanProcessState.INVALID
                        _errorMessage.value = validation.message
                        return@launch
                    }
                    BarcodeValidation.Valid -> Unit
                }
                if (!isCurrentOperation(owner, generation)) return@launch

                _processState.value = ScanProcessState.ASSESSING
                val assessment = scanRepository.assessBarcode(barcode, owner.profileId)
                if (!isCurrentOperation(owner, generation)) return@launch

                val success = when (assessment) {
                    ScanAssessment.Failed -> {
                        _processState.value = ScanProcessState.ERROR
                        _errorMessage.value = "Could not generate a safety verdict"
                        return@launch
                    }
                    ScanAssessment.UnknownVerdict -> {
                        _processState.value = ScanProcessState.ERROR
                        _errorMessage.value = "Could not generate a safety verdict"
                        return@launch
                    }
                    is ScanAssessment.Success -> assessment
                }

                val alternatives = if (success.verdict == ScanVerdict.SAFE) {
                    ScanAlternatives(emptyList(), null)
                } else {
                    _processState.value = ScanProcessState.FETCHING_ALTERNATIVES
                    scanRepository.loadAlternatives(owner.profileId, barcode, success.response.scanId)
                }
                if (!isCurrentOperation(owner, generation)) return@launch

                _verdictDetail.value = scanRepository.toVerdictDetail(
                    assessment = success,
                    fallbackBarcode = barcode,
                    alternatives = alternatives,
                )
                _processState.value = ScanProcessState.SUCCESS
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!isCurrentOperation(owner, generation)) return@launch
                _processState.value = ScanProcessState.ERROR
                _errorMessage.value = exception.message ?: "Product not found or network error"
            }
        }
    }

    fun resetState() {
        invalidateScan()
        _processState.value = ScanProcessState.IDLE
        _verdictDetail.value = null
        _errorMessage.value = null
    }

    private fun invalidateScan() {
        scanGeneration++
        scanJob?.cancel()
        scanJob = null
        scanOwner = null
    }

    private fun isCurrentOperation(
        owner: ActiveProfileManager.Selection,
        generation: Long,
    ): Boolean =
        generation == scanGeneration &&
            scanOwner == owner &&
            authSessionStore.accountKey.value == owner.accountKey &&
            activeProfileManager.isCurrent(owner.accountKey, owner.profileId)

    private companion object {
        const val PROFILE_SETUP_REQUIRED_MESSAGE =
            "Complete profile setup before scanning products."
    }
}
