package sg.edu.nus.iss.canmakan.shared.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class ScanRequest(val barcode: String)

data class ValidationResponse(
    val validFood: Boolean,
    val category: String?,
    val message: String?
)

data class AssessmentRequest(
    val barcode: String,
    val profileId: Long
)

data class AssessmentFinding(
    val restrictionCode: String?,
    val ingredientName: String?,
    val reason: String?
)

data class AssessmentResponse(
    val verdict: String,
    val explanation: String?,
    val findings: List<AssessmentFinding> = emptyList(),
    val tier: String? = null,
    val scanId: Long? = null,
    val productName: String? = null,
    val barcode: String? = null
)

interface CanMakanApiService {
    @POST("/api/scan/validate")
    suspend fun validateBarcode(@Body request: ScanRequest): Response<ValidationResponse>

    @POST("/api/scan/assess")
    suspend fun assessBarcode(
        @Body request: AssessmentRequest
    ): Response<AssessmentResponse>
}
