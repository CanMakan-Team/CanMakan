package sg.edu.nus.iss.canmakan.features.family.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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

data class ClaimInvitationRequestBody(
    @SerializedName("invitationToken") val invitationToken: String,
)

data class PendingInvitationResponse(
    @SerializedName("invitationId") val invitationId: Long,
    @SerializedName("familyId") val familyId: Long,
    @SerializedName("familyName") val familyName: String,
    @SerializedName("invitedByDisplayName") val invitedByDisplayName: String,
    @SerializedName("invitationToken") val invitationToken: String,
    @SerializedName("inviteCode") val inviteCode: String,
    @SerializedName("status") val status: String,
    @SerializedName("expiresAt") val expiresAt: String?,
    @SerializedName("expired") val expired: Boolean,
)

data class CreateDependantProfileRequestBody(
    @SerializedName("profileName") val profileName: String,
    @SerializedName("relationship") val relationship: String,
    @SerializedName("commonRequirements") val commonRequirements: List<String> = emptyList(),
    @SerializedName("restrictions") val restrictions: List<String> = emptyList(),
)

data class DependantProfileResponse(
    @SerializedName("profileId") val profileId: Long,
    @SerializedName("profileName") val profileName: String,
    @SerializedName("relationship") val relationship: String,
    @SerializedName("familyId") val familyId: Long,
)

data class ActiveProfileResponse(
    @SerializedName("profileId") val profileId: Long,
    @SerializedName("profileName") val profileName: String,
    @SerializedName("relationship") val relationship: String?,
    @SerializedName("familyId") val familyId: Long?,
    @SerializedName("isPrimary") val isPrimary: Boolean?,
)

data class SetActiveProfileRequestBody(
    @SerializedName("profileId") val profileId: Long,
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

    @GET("families/me/active-profile")
    suspend fun getActiveProfile(): Response<ActiveProfileResponse>

    @PUT("families/me/active-profile")
    suspend fun setActiveProfile(
        @Body request: SetActiveProfileRequestBody,
    ): Response<ActiveProfileResponse>

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

    @POST("families/me/invitations/claim")
    suspend fun claimInvitation(
        @Body request: ClaimInvitationRequestBody,
    ): Response<FamilyMeResponse>

    @GET("invitations/me")
    suspend fun listMyInvitations(): Response<List<PendingInvitationResponse>>

    @POST("invitations/{token}/accept")
    suspend fun acceptInvitation(
        @Path("token") token: String,
    ): Response<FamilyMeResponse>

    @POST("invitations/{token}/decline")
    suspend fun declineInvitation(
        @Path("token") token: String,
    ): Response<Unit>

    @POST("families/me/profiles")
    suspend fun createDependantProfile(
        @Body request: CreateDependantProfileRequestBody,
    ): Response<DependantProfileResponse>
}
