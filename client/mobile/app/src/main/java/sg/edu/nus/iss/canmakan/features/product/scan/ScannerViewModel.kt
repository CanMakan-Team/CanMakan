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
import sg.edu.nus.iss.canmakan.features.product.model.AlternativeProduct
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ProductFlag
import sg.edu.nus.iss.canmakan.features.product.model.ProductFlagCopy
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import sg.edu.nus.iss.canmakan.features.product.model.toUiModel
import sg.edu.nus.iss.canmakan.shared.network.AssessmentRequest
import sg.edu.nus.iss.canmakan.shared.network.AssessmentResponse
import sg.edu.nus.iss.canmakan.shared.network.CanMakanApiService
import sg.edu.nus.iss.canmakan.shared.network.ScanRequest
import javax.inject.Inject

/*
 * Represents the current state of the barcode scanning process.
 * IDLE: No barcode is being scanned.
 * VALIDATING: The barcode is being validated against the backend.
 * ASSESSING: The barcode is being assessed by the backend.
 * FETCHING_ALTERNATIVES: Safer alternatives are being loaded for Warning/Unsafe verdicts.
 * SUCCESS: The barcode has been successfully scanned and processed.
 * INVALID: The barcode is not valid.
 * ERROR: An error occurred during the scanning process.
 *
 * author Amelia
 * author Khai
 */
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
    private val apiService: CanMakanApiService,
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

    // Processes a barcode by sending it to the backend for validation and assessment.
    // Caller identity is attached as Bearer JWT by the auth interceptor.
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
                val validation = apiService.validateBarcode(ScanRequest(barcode))
                if (!isCurrentOperation(owner, generation)) return@launch

                val validationBody = validation.body()
                if (!validation.isSuccessful || validationBody == null) {
                    _processState.value = ScanProcessState.ERROR
                    _errorMessage.value = "Product not found or network error"
                    return@launch
                }

                if (!validationBody.validFood) {
                    _processState.value = ScanProcessState.INVALID
                    _errorMessage.value = validationBody.message
                    return@launch
                }

                _processState.value = ScanProcessState.ASSESSING
                val assessment = apiService.assessBarcode(
                    request = AssessmentRequest(barcode = barcode, profileId = owner.profileId),
                )
                if (!isCurrentOperation(owner, generation)) return@launch

                val assessmentBody = assessment.body()
                if (!assessment.isSuccessful || assessmentBody == null) {
                    _processState.value = ScanProcessState.ERROR
                    _errorMessage.value = "Could not generate a safety verdict"
                    return@launch
                }

                val verdict = parseVerdict(assessmentBody.verdict)
                val alternativesResult = if (verdict == ScanVerdict.SAFE) {
                    AlternativesResult(emptyList(), null)
                } else {
                    _processState.value = ScanProcessState.FETCHING_ALTERNATIVES
                    loadAlternatives(owner, barcode, assessmentBody.scanId)
                }
                if (!isCurrentOperation(owner, generation)) return@launch

                _verdictDetail.value = toVerdictDetail(
                    response = assessmentBody,
                    fallbackBarcode = barcode,
                    alternatives = alternativesResult.alternatives,
                    alternativesError = alternativesResult.errorMessage,
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

    private data class AlternativesResult(
        val alternatives: List<AlternativeProduct>,
        val errorMessage: String?,
    )

    private suspend fun loadAlternatives(
        owner: ActiveProfileManager.Selection,
        barcode: String,
        scanId: Long?,
    ): AlternativesResult {
        return try {
            val response = apiService.getRecommendations(
                profileId = owner.profileId,
                sourceBarcode = barcode,
                scanId = scanId,
            )
            if (!response.isSuccessful || response.body() == null) {
                AlternativesResult(
                    alternatives = emptyList(),
                    errorMessage = "Could not load alternatives",
                )
            } else {
                AlternativesResult(
                    alternatives = response.body()!!.alternatives.map { it.toUiModel() },
                    errorMessage = null,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            AlternativesResult(
                alternatives = emptyList(),
                errorMessage = "Could not load alternatives",
            )
        }
    }

    private fun parseVerdict(rawVerdict: String): ScanVerdict {
        return runCatching {
            ScanVerdict.valueOf(rawVerdict.uppercase())
        }.getOrDefault(ScanVerdict.WARNING)
    }

    // Converts the assessment response to a verdict detail.
    private fun toVerdictDetail(
        response: AssessmentResponse,
        fallbackBarcode: String,
        alternatives: List<AlternativeProduct>,
        alternativesError: String?,
    ): VerdictDetail {
        val verdict = parseVerdict(response.verdict)

        val flags = ProductFlagCopy.flagsFromFindings(
            findings = response.findings.map { finding ->
                Triple(finding.restrictionCode, finding.ingredientName, finding.reason)
            },
            summaryFallback = response.explanation,
        )

        return VerdictDetail(
            product = Product(
                productName = response.productName?.takeIf { it.isNotBlank() } ?: "Unknown product",
                brand = "",
                barcode = response.barcode?.takeIf { it.isNotBlank() } ?: fallbackBarcode,
            ),
            verdict = verdict,
            explanation = response.explanation,
            flags = flags,
            alternatives = alternatives,
            alternativesError = alternativesError,
            scanId = response.scanId,
        )
    }

    private companion object {
        const val PROFILE_SETUP_REQUIRED_MESSAGE =
            "Complete profile setup before scanning products."
    }
}
