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
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ProductFlag
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
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
            scanJob?.cancel()
            scanJob = null
            scanOwner = null
            _processState.value = ScanProcessState.ERROR
            _verdictDetail.value = null
            _errorMessage.value = "Complete profile setup before scanning products."
            return
        }
        scanJob?.cancel()
        scanOwner = owner
        _processState.value = ScanProcessState.VALIDATING
        _verdictDetail.value = null
        _errorMessage.value = null

        scanJob = viewModelScope.launch {
            try {
                // 1. Validate the barcode against the backend.
                val validation = apiService.validateBarcode(ScanRequest(barcode))
                if (!isCurrentOwner(owner)) return@launch

                // If the barcode is not valid, set the error message and return.
                val validationBody = validation.body()
                if (!validation.isSuccessful || validationBody == null) {
                    _processState.value = ScanProcessState.ERROR
                    _errorMessage.value = "Product not found or network error"
                    return@launch
                }

                // If the validFood flag is false, set the error message and return.
                if (!validationBody.validFood) {
                    _processState.value = ScanProcessState.INVALID
                    _errorMessage.value = validationBody.message
                    return@launch
                }

                // Set the process state to ASSESSING
                _processState.value = ScanProcessState.ASSESSING

                // 2. Send the barcode to the backend for assessment.
                val assessment = apiService.assessBarcode(
                    request = AssessmentRequest(barcode = barcode, profileId = profileId)
                )
                if (!isCurrentOwner(owner)) return@launch

                // If the assessment is not successful, set the error message and return.
                val assessmentBody = assessment.body()
                if (!assessment.isSuccessful || assessmentBody == null) {
                    _processState.value = ScanProcessState.ERROR
                    _errorMessage.value = "Could not generate a safety verdict"
                    return@launch
                }

                // If the assessment is successful, set the verdict detail and process state.
                _verdictDetail.value = toVerdictDetail(assessmentBody, barcode)
                _processState.value = ScanProcessState.SUCCESS
            } catch (exception: CancellationException) {
                throw exception
            } catch (e: Exception) {
                if (!isCurrentOwner(owner)) return@launch
                _processState.value = ScanProcessState.ERROR
                _errorMessage.value = e.message ?: "Product not found or network error"
            }
        }
    }

    fun resetState() {
        scanJob?.cancel()
        scanJob = null
        scanOwner = null
        _processState.value = ScanProcessState.IDLE
        _verdictDetail.value = null
        _errorMessage.value = null
    }

    private fun isCurrentOwner(owner: ActiveProfileManager.Selection): Boolean =
        scanOwner == owner &&
            authSessionStore.accountKey.value == owner.accountKey &&
            activeProfileManager.isCurrent(owner.accountKey, owner.profileId)

    // Converts the assessment response to a verdict detail.
    private fun toVerdictDetail(response: AssessmentResponse, fallbackBarcode: String): VerdictDetail {

        // Convert the verdict string to an enum value.
        val verdict = runCatching {
            ScanVerdict.valueOf(response.verdict.uppercase())
        }.getOrDefault(ScanVerdict.WARNING)

        // Create a list of product flags based on the assessment response.
        // If no flags are found, use the explanation or a default flag.
        val flags = response.findings.map { finding ->
            ProductFlag(
                category = finding.restrictionCode?.takeIf { it.isNotBlank() } ?: "INFO",
                label = finding.reason
                    ?.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(finding.ingredientName, finding.restrictionCode)
                        .joinToString(" · ")
                        .ifBlank { "Flagged by dietary rules" }
            )
        }.ifEmpty {
            response.explanation
                ?.takeIf { it.isNotBlank() }
                ?.let { listOf(ProductFlag("SUMMARY", it)) }
                ?: emptyList()
        }

        return VerdictDetail(
            product = Product(
                productName = response.productName?.takeIf { it.isNotBlank() } ?: "Unknown product",
                brand = "",
                barcode = response.barcode?.takeIf { it.isNotBlank() } ?: fallbackBarcode
            ),
            verdict = verdict,
            explanation = response.explanation,
            flags = flags
        )
    }
}
