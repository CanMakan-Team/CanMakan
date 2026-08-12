package sg.edu.nus.iss.canmakan.shared.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
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
    val barcode: String,
    val productName: String,
    val brand: String?,
    val matchReason: String?,
    val rankScore: Double?
)

data class RecommendationResponse(
    val sourceBarcode: String?,
    val alternatives: List<AlternativeProductDto> = emptyList()
)

interface CanMakanApiService {
    @POST("/api/scan/validate")
    suspend fun validateBarcode(@Body request: ScanRequest): Response<ValidationResponse>

    @POST("/api/scan/assess")
    suspend fun assessBarcode(
        @Body request: AssessmentRequest
    ): Response<AssessmentResponse>

    @GET("profiles/{profileId}/recommendations")
    suspend fun getRecommendations(
        @Path("profileId") profileId: Long,
        @Query("sourceBarcode") sourceBarcode: String,
        @Query("scanId") scanId: Long? = null
    ): Response<RecommendationResponse>
}
