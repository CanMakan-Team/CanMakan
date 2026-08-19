package sg.edu.nus.iss.canmakan.features.auth.onboarding

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.Response
import sg.edu.nus.iss.canmakan.features.family.data.ActiveProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.ClaimInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateDependantProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.DependantProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMemberRosterItem
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileApiService
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyRestrictionSumRes
import sg.edu.nus.iss.canmakan.features.family.data.InvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.NotificationPreferenceResponse
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.SetActiveProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.SetNotificationPreferenceRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.UserSearchResponse

class PendingInvitationClaimerTest {

    @Test
    fun claimForwardsTrimmedTokenToFamilyRepository() = runTest {
        val joined = FamilyMeResponse(3L, "Wong Family", "MEMBER", 77L, 14L)
        val api = RecordingClaimApi(Response.success(joined))
        val claimer = FamilyPendingInvitationClaimer(FamilyProfileRepository(api))

        claimer.claim("  invite-token  ")

        assertEquals("invite-token", api.lastToken)
    }

    private class RecordingClaimApi(
        private val claimResponse: Response<FamilyMeResponse>,
    ) : FamilyProfileApiService {
        var lastToken: String? = null

        override suspend fun getMyFamily(): Response<FamilyMeResponse> =
            Response.error(404, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun getFamilyMembers(): Response<List<FamilyMemberRosterItem>> =
            Response.success(emptyList())

        override suspend fun createFamily(request: CreateFamilyRequestBody): Response<FamilyMeResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun getProfilesByFamilyId(familyId: Long): List<FamilyProfileResponse> = emptyList()

        override suspend fun getActiveProfile(): Response<ActiveProfileResponse> =
            Response.error(404, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun setActiveProfile(request: SetActiveProfileRequestBody): Response<ActiveProfileResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun getNotificationPreference(): Response<NotificationPreferenceResponse> =
            Response.success(NotificationPreferenceResponse(true))

        override suspend fun setNotificationPreference(
            request: SetNotificationPreferenceRequestBody,
        ): Response<NotificationPreferenceResponse> =
            Response.success(NotificationPreferenceResponse(request.notificationsEnabled))

        override suspend fun getFamilyRestrictionSummary(): Response<FamilyRestrictionSumRes> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun searchUserByEmail(email: String): Response<UserSearchResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun createInvitation(request: CreateInvitationRequestBody): Response<InvitationResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun claimInvitation(request: ClaimInvitationRequestBody): Response<FamilyMeResponse> {
            lastToken = request.invitationToken
            return claimResponse
        }

        override suspend fun listMyInvitations(): Response<List<PendingInvitationResponse>> =
            Response.success(emptyList())

        override suspend fun acceptInvitation(token: String): Response<FamilyMeResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun declineInvitation(token: String): Response<Unit> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun createDependantProfile(
            request: CreateDependantProfileRequestBody,
        ): Response<DependantProfileResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
    }
}
