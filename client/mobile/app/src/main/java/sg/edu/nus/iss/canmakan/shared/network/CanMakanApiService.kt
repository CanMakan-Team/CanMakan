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

interface CanMakanApiService {
    @POST("/api/scan/validate")
    suspend fun validateBarcode(@Body request: ScanRequest): Response<ValidationResponse>
}