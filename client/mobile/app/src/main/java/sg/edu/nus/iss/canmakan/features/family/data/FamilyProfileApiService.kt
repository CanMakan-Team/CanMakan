package sg.edu.nus.iss.canmakan.features.family.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class UserSearchResponse(
    @SerializedName("userId") val userId: Long?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("maskedEmail") val maskedEmail: String,
    @SerializedName("accountStatus") val accountStatus: String,
    @SerializedName("familyLinkStatus") val familyLinkStatus: String,
)

data class CreateInvitationRequestBody(
    @SerializedName("email") val email: String,
)

data class InvitationResponse(
    @SerializedName("invitationId") val invitationId: Long,
    @SerializedName("invitedEmail") val invitedEmail: String,
    @SerializedName("invitationToken") val invitationToken: String,
    @SerializedName("inviteCode") val inviteCode: String,
    @SerializedName("inviteUrl") val inviteUrl: String,
    @SerializedName("status") val status: String,
    @SerializedName("expiresAt") val expiresAt: String?,
    @SerializedName("inviteeRegistered") val inviteeRegistered: Boolean,
)

interface FamilyProfileApiService {
    @GET("families/me")
    suspend fun getMyFamily(): Response<FamilyMeResponse>

    @POST("families")
    suspend fun createFamily(
        @Body request: CreateFamilyRequestBody,
    ): Response<FamilyMeResponse>

    @GET("families/{familyId}/profiles")
    suspend fun getProfilesByFamilyId(
        @Path("familyId") familyId: Long
    ): List<FamilyProfileResponse>

    @GET("families/me/restriction-summary")
    suspend fun getFamilyRestrictionSummary(): Response<FamilyRestrictionSumRes>

    @GET("families/me/user-search")
    suspend fun searchUserByEmail(
        @Query("email") email: String,
    ): Response<UserSearchResponse>

    @POST("families/me/invitations")
    suspend fun createInvitation(
        @Body request: CreateInvitationRequestBody,
    ): Response<InvitationResponse>
}
