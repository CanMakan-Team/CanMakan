package sg.edu.nus.iss.canmakan.features.auth.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

data class RegistrationRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("invitationToken") val invitationToken: String? = null,
) {
    override fun toString(): String {
        return "RegistrationRequest(email=$email, password=<redacted>, " +
            "invitationToken=${if (invitationToken.isNullOrBlank()) "null" else "<present>"})"
    }
}

data class InvitationPreviewResponse(
    @SerializedName("invitedEmail") val invitedEmail: String,
    @SerializedName("familyName") val familyName: String?,
    @SerializedName("expired") val expired: Boolean,
)

data class RegistrationResponse(
    @SerializedName("userId") val userId: Long,
    @SerializedName("email") val email: String,
    @SerializedName("active") val active: Boolean,
)

interface RegistrationApiService {
    @Headers("X-CanMakan-No-Retry: true")
    @POST("auth/register")
    suspend fun register(@Body request: RegistrationRequest): Response<RegistrationResponse>

    @GET("invitations/{token}/preview")
    suspend fun previewInvitation(
        @Path("token") token: String,
    ): Response<InvitationPreviewResponse>
}
