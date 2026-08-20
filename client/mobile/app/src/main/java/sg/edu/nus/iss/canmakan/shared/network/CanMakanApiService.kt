package sg.edu.nus.iss.canmakan.shared.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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

data class AlternativeProductDto(
    val barcode: String? = null,
    val productName: String? = null,
    val brand: String? = null,
    val matchReason: String? = null,
    val rankScore: Double? = null
)

data class RecommendationResponse(
    val sourceBarcode: String?,
    val alternatives: List<AlternativeProductDto> = emptyList()
)

// UC20: thumbs up/down feedback on a scan verdict. isPositive is required;
// userComments is optional free text and only ever sent alongside a thumbs
// down, but the field stays nullable either way.
data class ScanFeedbackRequest(
    val isPositive: Boolean,
    val userComments: String?
)

data class ScanFeedbackResponse(
    val id: Long,
    val scanId: Long,
    val isPositive: Boolean,
    val userComments: String?,
    val resolved: Boolean,
    val createdAt: String?
)

interface CanMakanApiService {
    @POST("scan/validate")
    suspend fun validateBarcode(@Body request: ScanRequest): Response<ValidationResponse>

    @Headers("X-CanMakan-No-Retry: true")
    @POST("scan/assess")
    suspend fun assessBarcode(
        @Body request: AssessmentRequest
    ): Response<AssessmentResponse>

    @Headers("X-CanMakan-No-Retry: true")
    @GET("profiles/{profileId}/recommendations")
    suspend fun getRecommendations(
        @Path("profileId") profileId: Long,
        @Query("sourceBarcode") sourceBarcode: String,
        @Query("scanId") scanId: Long? = null
    ): Response<RecommendationResponse>

    @Headers("X-CanMakan-No-Retry: true")
    @POST("scan/{scanId}/feedback")
    suspend fun submitScanFeedback(
        @Path("scanId") scanId: Long,
        @Body request: ScanFeedbackRequest
    ): Response<ScanFeedbackResponse>
}
