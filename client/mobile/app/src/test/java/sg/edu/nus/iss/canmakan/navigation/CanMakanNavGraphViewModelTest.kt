package sg.edu.nus.iss.canmakan.navigation

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionPersistence
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.family.data.ActiveProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMemberRosterItem
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileApiService
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyRestrictionSumRes
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationStore
import sg.edu.nus.iss.canmakan.features.family.data.ClaimInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateDependantProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.DependantProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.InvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.SetActiveProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.UserSearchResponse

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC19 / UC8: nav graph session identity and family membership")
class CanMakanNavGraphViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var activeProfileManager: ActiveProfileManager
    private lateinit var familyApi: RecordingFamilyProfileApiService
    private lateinit var viewModel: CanMakanNavGraphViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionStore = AuthSessionStore(FakeAuthSessionPersistence(), Gson())
        activeProfileManager = ActiveProfileManager()
        familyApi = RecordingFamilyProfileApiService()
        viewModel = CanMakanNavGraphViewModel(
            activeProfileManager = activeProfileManager,
            dietaryRestrictionRepo = FakeDietaryRestrictionRepository(),
            familyProfileRepository = FamilyProfileRepository(familyApi),
            authSessionStore = sessionStore,
            pendingInvitationStore = PendingInvitationStore(),
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun unauthenticatedSkipsFamilyApiAndClearsFamilyState() {
        assertFalse(viewModel.hasUserSession.value)
        assertFalse(viewModel.hasFamily.value)
        assertNull(viewModel.familyName.value)
        assertEquals(0, familyApi.meCalls)
        assertEquals(1, viewModel.profiles.value.size)
        assertEquals("Personal", viewModel.profiles.value.single().profileName)
    }

    @Test
    fun authenticatedUserWithoutFamilyLoadsActiveProfileFromServer() {
        familyApi.meResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        familyApi.activeProfileResponse = Response.success(
            ActiveProfileResponse(
                profileId = 42L,
                profileName = "Wong",
                relationship = "SELF",
                familyId = null,
                isPrimary = true,
            ),
        )

        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.hasUserSession.value)
        assertFalse(viewModel.hasFamily.value)
        assertNull(viewModel.familyName.value)
        assertEquals(1, familyApi.meCalls)
        assertEquals(0, familyApi.profilesCalls)
        assertEquals(1, familyApi.activeProfileCalls)
        assertEquals(42L, viewModel.profiles.value.single().id)
        assertEquals("Wong", viewModel.profiles.value.single().profileName)
        assertEquals(42L, activeProfileManager.currentProfileId.value)
    }

    @Test
    fun authenticatedUserWithFamilyLoadsMembershipAndProfiles() {
        familyApi.meResponse = Response.success(FAMILY_ME)
        familyApi.profiles = listOf(
            FamilyProfileResponse(
                id = 77L,
                profileName = "Wong",
                familyId = 50L,
                relationship = "Self",
                initials = "W",
                isPrimary = true,
            ),
        )

        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.hasUserSession.value)
        assertTrue(viewModel.hasFamily.value)
        assertEquals("Wong Family", viewModel.familyName.value)
        assertEquals(1, familyApi.meCalls)
        assertEquals(1, familyApi.profilesCalls)
        assertEquals(1, familyApi.activeProfileCalls)
        assertEquals(77L, viewModel.profiles.value.single().id)
        assertEquals(77L, activeProfileManager.currentProfileId.value)
    }

    @Test
    fun clearingSessionResetsFamilyStateWithoutFurtherMeCalls() {
        familyApi.meResponse = Response.success(FAMILY_ME)
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        val meCallsAfterLogin = familyApi.meCalls

        assertTrue(sessionStore.clearSession())
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.hasUserSession.value)
        assertFalse(viewModel.hasFamily.value)
        assertNull(viewModel.familyName.value)
        assertEquals(meCallsAfterLogin, familyApi.meCalls)
        assertEquals("Personal", viewModel.profiles.value.single().profileName)
    }

    @Test
    fun createFamilyCircleRequiresSignedInUser() {
        var successCalled = false

        viewModel.createFamilyCircle("Wong Family") { successCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Sign in before creating a family circle.", viewModel.createFamilyError.value)
        assertFalse(successCalled)
        assertEquals(0, familyApi.createCalls)
    }

    @Test
    fun createFamilyCircleRejectsWhenAlreadyInAFamily() {
        familyApi.meResponse = Response.success(FAMILY_ME)
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        var successCalled = false

        viewModel.createFamilyCircle("Another Family") { successCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("You already belong to a family circle.", viewModel.createFamilyError.value)
        assertFalse(successCalled)
        assertEquals(0, familyApi.createCalls)
    }

    @Test
    fun createFamilyCircleCreatesMembershipWhenSignedInWithoutFamily() {
        familyApi.meResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        familyApi.createResponse = Response.success(201, FAMILY_ME)
        familyApi.profiles = listOf(
            FamilyProfileResponse(
                id = 77L,
                profileName = "Wong",
                familyId = 50L,
                relationship = "Self",
                initials = "W",
                isPrimary = true,
            ),
        )
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        var successCalled = false

        viewModel.createFamilyCircle("Wong Family") { successCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(successCalled)
        assertNull(viewModel.createFamilyError.value)
        assertEquals(1, familyApi.createCalls)
        assertTrue(viewModel.hasFamily.value)
        assertEquals("Wong Family", viewModel.familyName.value)
        assertEquals(77L, activeProfileManager.currentProfileId.value)
    }

    @Test
    fun switchProfileCallsPutAndUpdatesActiveProfile() {
        familyApi.meResponse = Response.success(FAMILY_ME)
        familyApi.profiles = listOf(
            FamilyProfileResponse(
                id = 77L,
                profileName = "Wong",
                familyId = 50L,
                relationship = "Self",
                initials = "W",
                isPrimary = true,
            ),
            FamilyProfileResponse(
                id = 88L,
                profileName = "Child",
                familyId = 50L,
                relationship = "Child",
                initials = "C",
                isPrimary = false,
            ),
        )
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(77L, activeProfileManager.currentProfileId.value)

        viewModel.switchProfile(88L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, familyApi.setActiveProfileCalls)
        assertEquals(88L, activeProfileManager.currentProfileId.value)
        assertFalse(viewModel.isSwitchingProfile.value)
        assertNull(viewModel.switchProfileError.value)
    }

    @Test
    fun switchProfileForbiddenShowsErrorAndKeepsCurrentProfile() {
        familyApi.meResponse = Response.success(FAMILY_ME)
        familyApi.profiles = listOf(
            FamilyProfileResponse(
                id = 77L,
                profileName = "Wong",
                familyId = 50L,
                relationship = "Self",
                initials = "W",
                isPrimary = true,
            ),
        )
        familyApi.setActiveProfileResponse = Response.error(
            403,
            "{\"message\":\"That profile is not in your family circle.\"}"
                .toResponseBody("application/json".toMediaType()),
        )
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.switchProfile(88L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, familyApi.setActiveProfileCalls)
        assertEquals(77L, activeProfileManager.currentProfileId.value)
        assertFalse(viewModel.isSwitchingProfile.value)
        assertEquals(
            "That profile is not in your family circle.",
            viewModel.switchProfileError.value,
        )
    }

    @Test
    fun switchProfileNoOpWhenAlreadyActive() {
        familyApi.meResponse = Response.success(FAMILY_ME)
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.switchProfile(77L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, familyApi.setActiveProfileCalls)
    }

    private fun validSession(): AuthenticatedSession {
        return AuthenticatedSession(
            accessToken = "access-token",
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(14L, "person@example.com", AuthRole.USER),
        )
    }

    private class FakeAuthSessionPersistence : AuthSessionPersistence {
        private var serializedSession: String? = null

        override fun readSession(): String? = serializedSession

        override fun writeSession(serializedSession: String): Boolean {
            this.serializedSession = serializedSession
            return true
        }

        override fun clearSession(): Boolean {
            serializedSession = null
            return true
        }
    }

    private class FakeDietaryRestrictionRepository : DietaryRestrictionRepository {
        override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> = emptyList()

        override suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String> =
            emptyMap()

        override suspend fun saveDietaryRestrictionSelections(
            profileId: Long,
            selections: Map<Long, String>,
        ): Boolean = true
    }

    private class RecordingFamilyProfileApiService : FamilyProfileApiService {
        var meResponse: Response<FamilyMeResponse> = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        var createResponse: Response<FamilyMeResponse> = Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        var profiles: List<FamilyProfileResponse> = emptyList()
        var activeProfileResponse: Response<ActiveProfileResponse> = Response.success(
            ActiveProfileResponse(
                profileId = 77L,
                profileName = "Wong",
                relationship = "SELF",
                familyId = 50L,
                isPrimary = true,
            ),
        )
        var meCalls = 0
        var createCalls = 0
        var profilesCalls = 0
        var activeProfileCalls = 0
        var setActiveProfileCalls = 0
        var setActiveProfileResponse: Response<ActiveProfileResponse> = Response.success(
            ActiveProfileResponse(
                profileId = 88L,
                profileName = "Child",
                relationship = "CHILD",
                familyId = 50L,
                isPrimary = false,
            ),
        )

        override suspend fun getMyFamily(): Response<FamilyMeResponse> {
            meCalls++
            return meResponse
        }

        override suspend fun getFamilyMembers(): Response<List<FamilyMemberRosterItem>> =
            Response.success(emptyList())

        override suspend fun createFamily(
            request: CreateFamilyRequestBody,
        ): Response<FamilyMeResponse> {
            createCalls++
            val response = createResponse
            val body = response.body()
            if (response.isSuccessful && body != null) {
                meResponse = Response.success(body)
            }
            return response
        }

        override suspend fun getProfilesByFamilyId(familyId: Long): List<FamilyProfileResponse> {
            profilesCalls++
            return profiles
        }

        override suspend fun getActiveProfile(): Response<ActiveProfileResponse> {
            activeProfileCalls++
            return activeProfileResponse
        }

        override suspend fun setActiveProfile(
            request: SetActiveProfileRequestBody,
        ): Response<ActiveProfileResponse> {
            setActiveProfileCalls++
            return setActiveProfileResponse
        }

        override suspend fun getFamilyRestrictionSummary(): Response<FamilyRestrictionSumRes> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun searchUserByEmail(email: String): Response<UserSearchResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun createInvitation(
            request: CreateInvitationRequestBody,
        ): Response<InvitationResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun claimInvitation(
            request: ClaimInvitationRequestBody,
        ): Response<FamilyMeResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

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
