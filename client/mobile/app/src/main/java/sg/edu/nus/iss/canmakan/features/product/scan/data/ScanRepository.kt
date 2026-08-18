package sg.edu.nus.iss.canmakan.features.product.scan.data

import sg.edu.nus.iss.canmakan.features.product.model.AlternativeProduct
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import sg.edu.nus.iss.canmakan.shared.network.AssessmentResponse

sealed interface BarcodeValidation {
    data object Failed : BarcodeValidation
    data class Invalid(val message: String?) : BarcodeValidation
    data object Valid : BarcodeValidation
}

sealed interface ScanAssessment {
    data object Failed : ScanAssessment
    data object UnknownVerdict : ScanAssessment
    data class Success(
        val verdict: ScanVerdict,
        val response: AssessmentResponse,
    ) : ScanAssessment
}

data class ScanAlternatives(
    val alternatives: List<AlternativeProduct>,
    val errorMessage: String?,
)

interface ScanRepository {
    suspend fun validateBarcode(barcode: String): BarcodeValidation

    suspend fun assessBarcode(barcode: String, profileId: Long): ScanAssessment

    suspend fun loadAlternatives(
        profileId: Long,
        barcode: String,
        scanId: Long?,
    ): ScanAlternatives

    fun toVerdictDetail(
        assessment: ScanAssessment.Success,
        fallbackBarcode: String,
        alternatives: ScanAlternatives,
    ): VerdictDetail

    suspend fun submitFeedback(
        scanId: Long,
        isPositive: Boolean,
        comment: String?,
    ): Boolean
}
