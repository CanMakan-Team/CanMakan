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
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.SetActiveProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.UserSearchResponse

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC9: InviteFamilyMemberViewModel")
class InviteFamilyMemberViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var familyApi: RecordingFamilyProfileApiService
    private lateinit var viewModel: InviteFamilyMemberViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionStore = AuthSessionStore(FakeAuthSessionPersistence(), Gson())
        familyApi = RecordingFamilyProfileApiService()
        viewModel = InviteFamilyMemberViewModel(
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
    fun blankEmailShowsErrorWithoutCallingApi() = runTest {
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.invite()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Enter an email address.", viewModel.uiState.value.errorMessage)
        assertEquals(0, familyApi.createInvitationCalls)
        assertFalse(viewModel.uiState.value.inviteSucceeded)
    }

    @Test
    fun linkedUserConflictSurfacesBackendMessage() = runTest {
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        familyApi.createInvitationResponse = Response.error(
            409,
            """{"message":"That user already belongs to a family circle."}"""
                .toResponseBody("application/json".toMediaType()),
        )

        viewModel.updateEmail("linked@example.com")
        viewModel.invite()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "That user already belongs to a family circle.",
            viewModel.uiState.value.errorMessage,
        )
        assertFalse(viewModel.uiState.value.inviteSucceeded)
        assertEquals(1, familyApi.createInvitationCalls)
    }

    @Test
    fun successfulInviteSetsInviteSucceededAndEmailSent() = runTest {
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        familyApi.createInvitationResponse = Response.success(
            InvitationResponse(
                invitationId = 1L,
                invitedEmail = "new@example.com",
                invitationToken = "token",
                inviteCode = "ABCD1234",
                inviteUrl = "http://localhost:5173/invite/token",
                status = "PENDING",
                expiresAt = null,
                inviteeRegistered = false,
                emailSent = true,
            ),
        )

        viewModel.updateEmail("new@example.com")
        viewModel.invite()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.inviteSucceeded)
        assertTrue(viewModel.uiState.value.emailSent)
        assertEquals(1, familyApi.createInvitationCalls)

        viewModel.consumeInviteResult()
        assertFalse(viewModel.uiState.value.inviteSucceeded)
        assertFalse(viewModel.uiState.value.emailSent)
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
        var createInvitationResponse: Response<InvitationResponse> = Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        var createInvitationCalls = 0

        override suspend fun getMyFamily(): Response<FamilyMeResponse> =
            Response.error(404, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun getFamilyMembers(): Response<List<FamilyMemberRosterItem>> =
            Response.success(emptyList())

        override suspend fun createFamily(
            request: CreateFamilyRequestBody,
        ): Response<FamilyMeResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun getProfilesByFamilyId(familyId: Long): List<FamilyProfileResponse> =
            emptyList()

        override suspend fun getActiveProfile(): Response<ActiveProfileResponse> =
            Response.error(404, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun setActiveProfile(
            request: SetActiveProfileRequestBody,
        ): Response<ActiveProfileResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun getFamilyRestrictionSummary(): Response<FamilyRestrictionSumRes> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun searchUserByEmail(email: String): Response<UserSearchResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun createInvitation(
            request: CreateInvitationRequestBody,
        ): Response<InvitationResponse> {
            createInvitationCalls++
            return createInvitationResponse
        }

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
}
