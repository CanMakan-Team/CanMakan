package sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.Response
import sg.edu.nus.iss.canmakan.features.family.data.ActiveProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.ClaimInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateDependantProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.DependantProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileApiService
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyRestrictionSumRes
import sg.edu.nus.iss.canmakan.features.family.data.InvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.SetActiveProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.UserSearchResponse

class FamilyExistingSelfProfileResolverTest {
    @Test
    fun familySelfProfileIdIsAuthoritative() = runTest {
        val api = FakeFamilyApi(
            familyResponse = Response.success(
                FamilyMeResponse(10L, "Family", "MEMBER", 77L, 1L),
            ),
        )

        val result = resolver(api).resolveActiveSelfProfileId()

        assertEquals(77L, result)
        assertEquals(0, api.activeProfileCalls)
    }

    @Test
    fun standaloneActiveProfileMustExplicitlyBeSelf() = runTest {
        val api = FakeFamilyApi(
            activeResponse = Response.success(activeProfile(88L, "SELF")),
        )

        assertEquals(88L, resolver(api).resolveActiveSelfProfileId())
    }

    @Test
    fun dependantActiveProfileIsRejected() = runTest {
        val api = FakeFamilyApi(
            activeResponse = Response.success(activeProfile(89L, "CHILD")),
        )

        val outcome = runCatching { resolver(api).resolveActiveSelfProfileId() }

        assertTrue(outcome.isFailure)
    }

    @Test
    fun nonpositiveResolvedIdIsRejected() = runTest {
        listOf(0L, -1L).forEach { profileId ->
            val familyApi = FakeFamilyApi(
                familyResponse = Response.success(
                    FamilyMeResponse(10L, "Family", "MEMBER", profileId, 1L),
                ),
            )
            val standaloneApi = FakeFamilyApi(
                activeResponse = Response.success(activeProfile(profileId, "SELF")),
            )

            assertTrue(runCatching { resolver(familyApi).resolveActiveSelfProfileId() }.isFailure)
            assertTrue(runCatching { resolver(standaloneApi).resolveActiveSelfProfileId() }.isFailure)
        }
    }

    private fun resolver(api: FamilyProfileApiService): FamilyExistingSelfProfileResolver {
        return FamilyExistingSelfProfileResolver(FamilyProfileRepository(api))
    }

    private fun activeProfile(profileId: Long, relationship: String) = ActiveProfileResponse(
        profileId = profileId,
        profileName = "Profile",
        relationship = relationship,
        familyId = null,
        isPrimary = true,
    )

    private class FakeFamilyApi(
        private val familyResponse: Response<FamilyMeResponse> = notFound(),
        private val activeResponse: Response<ActiveProfileResponse> = notFound(),
    ) : FamilyProfileApiService {
        var activeProfileCalls = 0

        override suspend fun getMyFamily(): Response<FamilyMeResponse> = familyResponse

        override suspend fun getActiveProfile(): Response<ActiveProfileResponse> {
            activeProfileCalls++
            return activeResponse
        }

        override suspend fun createFamily(request: CreateFamilyRequestBody): Response<FamilyMeResponse> =
            error("unused")

        override suspend fun getProfilesByFamilyId(familyId: Long): List<FamilyProfileResponse> =
            error("unused")

        override suspend fun setActiveProfile(
            request: SetActiveProfileRequestBody,
        ): Response<ActiveProfileResponse> = error("unused")

        override suspend fun getFamilyRestrictionSummary(): Response<FamilyRestrictionSumRes> =
            error("unused")

        override suspend fun searchUserByEmail(email: String): Response<UserSearchResponse> =
            error("unused")

        override suspend fun createInvitation(
            request: CreateInvitationRequestBody,
        ): Response<InvitationResponse> = error("unused")

        override suspend fun claimInvitation(
            request: ClaimInvitationRequestBody,
        ): Response<FamilyMeResponse> = error("unused")

        override suspend fun listMyInvitations(): Response<List<PendingInvitationResponse>> =
            error("unused")

        override suspend fun acceptInvitation(token: String): Response<FamilyMeResponse> =
            error("unused")

        override suspend fun declineInvitation(token: String): Response<Unit> = error("unused")

        override suspend fun createDependantProfile(
            request: CreateDependantProfileRequestBody,
        ): Response<DependantProfileResponse> = error("unused")
    }

    private companion object {
        fun <T> notFound(): Response<T> = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
    }
}
