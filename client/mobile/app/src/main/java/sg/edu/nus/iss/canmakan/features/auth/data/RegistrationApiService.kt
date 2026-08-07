package sg.edu.nus.iss.canmakan.features.auth.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

data class RegistrationRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
) {
    override fun toString(): String {
        return "RegistrationRequest(email=$email, password=<redacted>)"
    }
}

data class RegistrationResponse(
    @SerializedName("userId") val userId: Long,
    @SerializedName("email") val email: String,
    @SerializedName("active") val active: Boolean,
)

interface RegistrationApiService {
    @Headers("X-CanMakan-No-Retry: true")
    @POST("auth/register")
    suspend fun register(@Body request: RegistrationRequest): Response<RegistrationResponse>
}
