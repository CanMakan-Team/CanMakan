package sg.edu.nus.iss.canmakan.features.product.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.product.model.AlternativeProduct
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ProductFlag
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
    private val apiService: CanMakanApiService
) : ViewModel() {
    private val _processState = MutableStateFlow(ScanProcessState.IDLE)
    val processState: StateFlow<ScanProcessState> = _processState.asStateFlow()

    private val _verdictDetail = MutableStateFlow<VerdictDetail?>(null)
    val verdictDetail: StateFlow<VerdictDetail?> = _verdictDetail.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Processes a barcode by sending it to the backend for validation and assessment.
    // Caller identity is attached as Bearer JWT by the auth interceptor.
    fun processBarcode(barcode: String, profileId: Long) {
        _processState.value = ScanProcessState.VALIDATING
        _verdictDetail.value = null
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // 1. Validate the barcode against the backend.
                val validation = apiService.validateBarcode(ScanRequest(barcode))

                // If the barcode is not valid, set the error message and return.
                if (!validation.isSuccessful || validation.body() == null) {
                    _processState.value = ScanProcessState.ERROR
                    _errorMessage.value = "Product not found or network error"
                    return@launch
                }

                // If the validFood flag is false, set the error message and return.
                if (!validation.body()!!.validFood) {
                    _processState.value = ScanProcessState.INVALID
                    _errorMessage.value = validation.body()!!.message
                    return@launch
                }

                // Set the process state to ASSESSING
                _processState.value = ScanProcessState.ASSESSING

                // 2. Send the barcode to the backend for assessment.
                val assessment = apiService.assessBarcode(
                    request = AssessmentRequest(barcode = barcode, profileId = profileId)
                )

                // If the assessment is not successful, set the error message and return.
                if (!assessment.isSuccessful || assessment.body() == null) {
                    _processState.value = ScanProcessState.ERROR
                    _errorMessage.value = "Could not generate a safety verdict"
                    return@launch
                }

                val assessmentBody = assessment.body()!!
                val verdict = parseVerdict(assessmentBody.verdict)

                val alternativesResult = if (verdict == ScanVerdict.SAFE) {
                    AlternativesResult(emptyList(), null)
                } else {
                    _processState.value = ScanProcessState.FETCHING_ALTERNATIVES
                    loadAlternatives(profileId, barcode, assessmentBody.scanId)
                }

                _verdictDetail.value = toVerdictDetail(
                    response = assessmentBody,
                    fallbackBarcode = barcode,
                    alternatives = alternativesResult.alternatives,
                    alternativesError = alternativesResult.errorMessage
                )
                _processState.value = ScanProcessState.SUCCESS
            } catch (e: Exception) {
                _processState.value = ScanProcessState.ERROR
                _errorMessage.value = e.message ?: "Product not found or network error"
            }
        }
    }

    fun resetState() {
        _processState.value = ScanProcessState.IDLE
        _verdictDetail.value = null
        _errorMessage.value = null
    }

    private data class AlternativesResult(
        val alternatives: List<AlternativeProduct>,
        val errorMessage: String?
    )

    private suspend fun loadAlternatives(
        profileId: Long,
        barcode: String,
        scanId: Long?
    ): AlternativesResult {
        return try {
            val response = apiService.getRecommendations(
                profileId = profileId,
                sourceBarcode = barcode,
                scanId = scanId
            )
            if (!response.isSuccessful || response.body() == null) {
                AlternativesResult(
                    alternatives = emptyList(),
                    errorMessage = "Could not load alternatives"
                )
            } else {
                AlternativesResult(
                    alternatives = response.body()!!.alternatives.map { it.toUiModel() },
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            AlternativesResult(
                alternatives = emptyList(),
                errorMessage = "Could not load alternatives"
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
        alternativesError: String?
    ): VerdictDetail {
        val verdict = parseVerdict(response.verdict)

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
            flags = flags,
            alternatives = alternatives,
            alternativesError = alternativesError
        )
    }
}
