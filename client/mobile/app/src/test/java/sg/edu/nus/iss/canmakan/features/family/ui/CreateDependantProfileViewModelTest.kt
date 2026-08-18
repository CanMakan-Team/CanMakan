package sg.edu.nus.iss.canmakan.features.family.ui

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import sg.edu.nus.iss.canmakan.features.family.model.RelationshipToAdmin

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC9: CreateDependantProfileViewModel")
class CreateDependantProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var familyApi: RecordingFamilyProfileApiService
    private lateinit var viewModel: CreateDependantProfileViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionStore = AuthSessionStore(FakeAuthSessionPersistence(), Gson())
        familyApi = RecordingFamilyProfileApiService()
        viewModel = CreateDependantProfileViewModel(
            familyProfileRepository = FamilyProfileRepository(familyApi),
            authSessionStore = sessionStore,
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun blankNameShowsErrorWithoutCallingApi() = runTest {
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateRelationship(RelationshipToAdmin.CHILD)
        viewModel.create()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Name is required.", viewModel.uiState.value.errorMessage)
        assertEquals(0, familyApi.createDependantCalls)
        assertFalse(viewModel.uiState.value.created)
    }

    @Test
    fun missingRelationshipShowsErrorWithoutCallingApi() = runTest {
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateProfileName("Alex")
        viewModel.create()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Relationship is required.", viewModel.uiState.value.errorMessage)
        assertEquals(0, familyApi.createDependantCalls)
    }

    @Test
    fun successMarksCreated() = runTest {
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        familyApi.createDependantResponse = Response.success(
            DependantProfileResponse(9L, "Alex", "CHILD", 1L),
        )

        viewModel.updateProfileName("Alex")
        viewModel.updateRelationship(RelationshipToAdmin.CHILD)
        viewModel.create()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.created)
        assertEquals(1, familyApi.createDependantCalls)
        assertEquals("CHILD", familyApi.lastRelationship)
    }

    @Test
    fun accountChangeResetsForm() = runTest {
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Alex")

        sessionStore.saveSession(
            AuthenticatedSession(
                accessToken = "other",
                tokenType = "Bearer",
                expiresIn = 900,
                user = AuthenticatedUser(22L, "other@example.com", AuthRole.USER),
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.profileName)
        assertFalse(viewModel.uiState.value.created)
    }

    private fun validSession(): AuthenticatedSession {
        return AuthenticatedSession(
            accessToken = "access-token",
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(10L, "admin@example.com", AuthRole.USER),
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

    private class RecordingFamilyProfileApiService : FamilyProfileApiService {
        var createDependantResponse: Response<DependantProfileResponse> = Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        var createDependantCalls = 0
        var lastRelationship: String? = null

        override suspend fun getMyFamily() =
            Response.error<FamilyMeResponse>(404, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun getFamilyMembers() = Response.success(emptyList<FamilyMemberRosterItem>())
        override suspend fun createFamily(request: CreateFamilyRequestBody) =
            Response.error<FamilyMeResponse>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun getProfilesByFamilyId(familyId: Long) = emptyList<FamilyProfileResponse>()
        override suspend fun getActiveProfile() =
            Response.error<ActiveProfileResponse>(404, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun setActiveProfile(request: SetActiveProfileRequestBody) =
            Response.error<ActiveProfileResponse>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun getNotificationPreference() =
            Response.success(NotificationPreferenceResponse(true))
        override suspend fun setNotificationPreference(request: SetNotificationPreferenceRequestBody) =
            Response.success(NotificationPreferenceResponse(request.notificationsEnabled))
        override suspend fun getFamilyRestrictionSummary() =
            Response.error<FamilyRestrictionSumRes>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun searchUserByEmail(email: String) =
            Response.error<UserSearchResponse>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun createInvitation(request: CreateInvitationRequestBody) =
            Response.error<InvitationResponse>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun claimInvitation(request: ClaimInvitationRequestBody) =
            Response.error<FamilyMeResponse>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun listMyInvitations() = Response.success(emptyList<PendingInvitationResponse>())
        override suspend fun acceptInvitation(token: String) =
            Response.error<FamilyMeResponse>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun declineInvitation(token: String) =
            Response.error<Unit>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun createDependantProfile(request: CreateDependantProfileRequestBody): Response<DependantProfileResponse> {
            createDependantCalls++
            lastRelationship = request.relationship
            return createDependantResponse
        }
    }
}
