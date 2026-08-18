package sg.edu.nus.iss.canmakan.features.family

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.Response
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import sg.edu.nus.iss.canmakan.features.family.data.ActiveProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.ClaimInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateDependantProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.DependantProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyApiException
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

class FamilyContextLoaderTest {

    private val accountKey = AuthAccountKey(userId = 14L, sessionGeneration = 1L)

    @Test
    fun missingActiveProfileClearsLocalSelectionAndReturnsEmptyProfiles() = runBlocking {
        val manager = ActiveProfileManager().also { it.switchProfile(accountKey, 9L) }
        val api = FakeFamilyApi(
            meResponse = Response.success(FAMILY_ME),
            activeProfileResponse = Response.error(
                404,
                "{}".toResponseBody("application/json".toMediaType()),
            ),
        )
        val loader = FamilyContextLoader(FamilyProfileRepository(api), manager)

        val snapshot = loader.load(accountKey) { true }

        assertEquals(true, snapshot?.hasFamily)
        assertEquals("Wong Family", snapshot?.familyName)
        assertTrue(snapshot?.showManageFamilyActions == true)
        assertTrue(snapshot?.profiles?.isEmpty() == true)
        assertNull(snapshot?.resolvedProfileId)
        assertNull(manager.selection.value)
    }

    @Test
    fun noFamilyUsesActiveProfileAsTheOnlyShellProfile() = runBlocking {
        val api = FakeFamilyApi(
            meResponse = Response.error(404, "{}".toResponseBody("application/json".toMediaType())),
            activeProfileResponse = Response.success(
                ActiveProfileResponse(42L, "Wong", "SELF", null, null),
            ),
        )
        val snapshot = FamilyContextLoader(FamilyProfileRepository(api), ActiveProfileManager())
            .load(accountKey) { true }

        assertFalse(snapshot!!.hasFamily)
        assertEquals(42L, snapshot.resolvedProfileId)
        assertEquals(42L, snapshot.profiles.single().id)
        assertEquals(0L, snapshot.profiles.single().familyId)
        assertFalse(snapshot.profiles.single().isPrimary)
        assertEquals("WO", snapshot.profiles.single().initials)
        assertEquals("SELF", snapshot.profiles.single().relationship)
    }

    @Test
    fun familyFallsBackToSelfProfileWhenServerIdIsMissingFromRoster() = runBlocking {
        val api = FakeFamilyApi(
            meResponse = Response.success(FAMILY_ME),
            profiles = listOf(
                FamilyProfileResponse(77L, "Self", 50L, "SELF", "S", true),
                FamilyProfileResponse(88L, "Child", 50L, "CHILD", "C", false),
            ),
            activeProfileResponse = Response.success(
                ActiveProfileResponse(999L, "Gone", "SELF", 50L, true),
            ),
        )
        val snapshot = FamilyContextLoader(FamilyProfileRepository(api), ActiveProfileManager())
            .load(accountKey) { true }

        assertEquals(77L, snapshot?.resolvedProfileId)
        assertEquals(listOf(77L, 88L), snapshot?.profiles?.map { it.id })
    }

    @Test
    fun familyFallsBackToFirstRosterProfileWhenSelfIsAlsoMissing() = runBlocking {
        val api = FakeFamilyApi(
            meResponse = Response.success(FAMILY_ME.copy(selfProfileId = 1L, memberRole = "MEMBER")),
            profiles = listOf(
                FamilyProfileResponse(88L, "Child", 50L, "CHILD", "C", false),
            ),
            activeProfileResponse = Response.success(
                ActiveProfileResponse(999L, "Gone", "CHILD", 50L, false),
            ),
        )
        val snapshot = FamilyContextLoader(FamilyProfileRepository(api), ActiveProfileManager())
            .load(accountKey) { true }

        assertEquals(88L, snapshot?.resolvedProfileId)
        assertFalse(snapshot!!.showManageFamilyActions)
    }

    @Test
    fun emptyRosterUsesMappedActiveProfile() = runBlocking {
        val api = FakeFamilyApi(
            meResponse = Response.success(FAMILY_ME),
            profiles = emptyList(),
            activeProfileResponse = Response.success(
                ActiveProfileResponse(77L, "Wong", "SELF", 50L, true),
            ),
        )
        val snapshot = FamilyContextLoader(FamilyProfileRepository(api), ActiveProfileManager())
            .load(accountKey) { true }

        assertEquals(77L, snapshot?.profiles?.single()?.id)
        assertEquals(77L, snapshot?.resolvedProfileId)
    }

    @Test
    fun non404ActiveProfileErrorsPropagate() {
        val api = FakeFamilyApi(
            meResponse = Response.success(FAMILY_ME),
            activeProfileResponse = Response.error(
                500,
                """{"message":"down"}""".toResponseBody("application/json".toMediaType()),
            ),
        )
        val exception = assertThrows(FamilyApiException::class.java) {
            runBlocking {
                FamilyContextLoader(FamilyProfileRepository(api), ActiveProfileManager())
                    .load(accountKey) { true }
            }
        }
        assertEquals(500, exception.statusCode)
    }

    @Test
    fun staleAccountCheckAfterMembershipFetchAborts() = runBlocking {
        val api = FakeFamilyApi(meResponse = Response.success(FAMILY_ME))
        val snapshot = FamilyContextLoader(FamilyProfileRepository(api), ActiveProfileManager())
            .load(accountKey) { false }

        assertNull(snapshot)
        assertEquals(0, api.activeProfileCalls)
    }

    private class FakeFamilyApi(
        var meResponse: Response<FamilyMeResponse> = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        ),
        var profiles: List<FamilyProfileResponse> = emptyList(),
        var activeProfileResponse: Response<ActiveProfileResponse> = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        ),
    ) : FamilyProfileApiService {
        var activeProfileCalls = 0
        private val emptyError = "{}".toResponseBody("application/json".toMediaType())

        override suspend fun getMyFamily() = meResponse
        override suspend fun getFamilyMembers() = Response.success(emptyList<FamilyMemberRosterItem>())
        override suspend fun createFamily(request: CreateFamilyRequestBody) =
            Response.error<FamilyMeResponse>(500, emptyError)
        override suspend fun getProfilesByFamilyId(familyId: Long) = profiles
        override suspend fun getActiveProfile(): Response<ActiveProfileResponse> {
            activeProfileCalls++
            return activeProfileResponse
        }
        override suspend fun setActiveProfile(request: SetActiveProfileRequestBody) =
            Response.error<ActiveProfileResponse>(500, emptyError)
        override suspend fun getNotificationPreference() =
            Response.success(NotificationPreferenceResponse(true))
        override suspend fun setNotificationPreference(request: SetNotificationPreferenceRequestBody) =
            Response.success(NotificationPreferenceResponse(request.notificationsEnabled))
        override suspend fun getFamilyRestrictionSummary() =
            Response.error<FamilyRestrictionSumRes>(500, emptyError)
        override suspend fun searchUserByEmail(email: String) =
            Response.error<UserSearchResponse>(500, emptyError)
        override suspend fun createInvitation(request: CreateInvitationRequestBody) =
            Response.error<InvitationResponse>(500, emptyError)
        override suspend fun claimInvitation(request: ClaimInvitationRequestBody) =
            Response.error<FamilyMeResponse>(500, emptyError)
        override suspend fun listMyInvitations() = Response.success(emptyList<PendingInvitationResponse>())
        override suspend fun acceptInvitation(token: String) = Response.error<FamilyMeResponse>(500, emptyError)
        override suspend fun declineInvitation(token: String) = Response.error<Unit>(500, emptyError)
        override suspend fun createDependantProfile(request: CreateDependantProfileRequestBody) =
            Response.error<DependantProfileResponse>(500, emptyError)
    }

    private companion object {
        val FAMILY_ME = FamilyMeResponse(
            familyId = 50L,
            familyName = "Wong Family",
            memberRole = "PRIMARY_ADMIN",
            selfProfileId = 77L,
            createdByUserId = 14L,
        )
    }
}
