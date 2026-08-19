package sg.edu.nus.iss.canmakan.features.product.scan.data

import kotlinx.coroutines.CancellationException
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import sg.edu.nus.iss.canmakan.shared.network.AssessmentRequest
import sg.edu.nus.iss.canmakan.shared.network.CanMakanApiService
import sg.edu.nus.iss.canmakan.shared.network.ScanFeedbackRequest
import sg.edu.nus.iss.canmakan.shared.network.ScanRequest
import javax.inject.Inject

class ServerScanRepository @Inject constructor(
    private val apiService: CanMakanApiService,
) : ScanRepository {

    override suspend fun validateBarcode(barcode: String): BarcodeValidation {
        val response = apiService.validateBarcode(ScanRequest(barcode))
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            return BarcodeValidation.Failed
        }
        if (!body.validFood) {
            return BarcodeValidation.Invalid(body.message)
        }
        return BarcodeValidation.Valid
    }

    override suspend fun assessBarcode(barcode: String, profileId: Long): ScanAssessment {
        val response = apiService.assessBarcode(
            AssessmentRequest(barcode = barcode, profileId = profileId),
        )
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            return ScanAssessment.Failed
        }
        val verdict = ScanVerdictMapper.parseVerdict(body.verdict)
            ?: return ScanAssessment.UnknownVerdict
        return ScanAssessment.Success(verdict = verdict, response = body)
    }

    override suspend fun loadAlternatives(
        profileId: Long,
        barcode: String,
        scanId: Long?,
    ): ScanAlternatives {
        return try {
            val response = apiService.getRecommendations(
                profileId = profileId,
                sourceBarcode = barcode,
                scanId = scanId,
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                ScanAlternatives(alternatives = emptyList(), errorMessage = ALTERNATIVES_ERROR)
            } else {
                ScanAlternatives(
                    alternatives = ScanVerdictMapper.alternativesFrom(body),
                    errorMessage = null,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            ScanAlternatives(alternatives = emptyList(), errorMessage = ALTERNATIVES_ERROR)
        }
    }

    override fun toVerdictDetail(
        assessment: ScanAssessment.Success,
        fallbackBarcode: String,
        alternatives: ScanAlternatives,
    ): VerdictDetail {
        return ScanVerdictMapper.toVerdictDetail(
            response = assessment.response,
            verdict = assessment.verdict,
            fallbackBarcode = fallbackBarcode,
            alternatives = alternatives.alternatives,
            alternativesError = alternatives.errorMessage,
        )
    }

    override suspend fun submitFeedback(
        scanId: Long,
        isPositive: Boolean,
        comment: String?,
    ): Boolean {
        val trimmedComment = comment?.trim()?.takeIf { it.isNotEmpty() }
        val response = apiService.submitScanFeedback(
            scanId = scanId,
            request = ScanFeedbackRequest(isPositive = isPositive, userComments = trimmedComment),
        )
        return response.isSuccessful
    }

    private companion object {
        const val ALTERNATIVES_ERROR = "Could not load alternatives"
    }
}
