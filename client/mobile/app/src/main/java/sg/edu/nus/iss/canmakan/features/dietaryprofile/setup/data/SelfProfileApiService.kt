package sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

enum class ProfileRestrictionSeverity {
    STRICT_AVOID,
    INTOLERANCE,
}

data class CreateSelfProfileRequest(
    @SerializedName("profileName") val profileName: String,
    @SerializedName("restrictions")
    val restrictions: Map<Long, ProfileRestrictionSeverity>,
)

data class SelfProfileResponse(
    @SerializedName("profileId") val profileId: Long?,
    @SerializedName("profileName") val profileName: String?,
    @SerializedName("relationship") val relationship: String?,
    @SerializedName("active") val active: Boolean?,
    @SerializedName("restrictions") val restrictions: Map<Long, String>?,
)

interface SelfProfileApiService {
    @Headers("X-CanMakan-No-Retry: true")
    @POST("profiles/me")
    suspend fun createSelfProfile(
        @Body request: CreateSelfProfileRequest,
    ): Response<SelfProfileResponse>
}
