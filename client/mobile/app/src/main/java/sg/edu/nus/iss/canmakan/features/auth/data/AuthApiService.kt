package sg.edu.nus.iss.canmakan.features.auth.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

enum class AuthRole {
    USER,
    ADMIN,
}

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
) {
    override fun toString(): String {
        return "LoginRequest(email=$email, password=<redacted>)"
    }
}

/**
 * Wire representation shared by login/refresh and /me responses.
 *
 * Fields are nullable so missing or unknown Gson values can be rejected by the
 * repository as an invalid server response instead of escaping as unsafe Kotlin
 * non-null values. In particular, Gson maps an unknown enum value to null.
 */
data class AuthenticatedUserResponse(
    @SerializedName("userId") val userId: Long?,
    @SerializedName("email") val email: String?,
    @SerializedName("role") val role: AuthRole?,
)

data class AuthResponse(
    @SerializedName("accessToken") val accessToken: String?,
    @SerializedName("tokenType") val tokenType: String?,
    @SerializedName("expiresIn") val expiresIn: Long?,
    @SerializedName("user") val user: AuthenticatedUserResponse?,
) {
    override fun toString(): String {
        return "AuthResponse(accessToken=<redacted>, tokenType=$tokenType, " +
            "expiresIn=$expiresIn, user=$user)"
    }
}

interface AuthApiService {
    @Headers("X-CanMakan-No-Retry: true")
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @Headers("X-CanMakan-No-Retry: true")
    @GET("auth/me")
    suspend fun getCurrentUser(): Response<AuthenticatedUserResponse>
}
