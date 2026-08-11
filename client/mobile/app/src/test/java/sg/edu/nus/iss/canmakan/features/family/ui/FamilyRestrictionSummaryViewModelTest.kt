package sg.edu.nus.iss.canmakan.features.family.ui

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
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.ClaimInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateDependantProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.DependantProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeRestrictionDetail
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeRestrictionSum
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileApiService
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyRestrictionSumRes
import sg.edu.nus.iss.canmakan.features.family.data.InvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.SetActiveProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.UserSearchResponse

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC6: family restriction summary session gate")
class FamilyRestrictionSummaryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var familyApi: RecordingFamilyProfileApiService
    private lateinit var viewModel: FamilyRestrictionSummaryViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionStore = AuthSessionStore(FakeAuthSessionPersistence(), Gson())
        familyApi = RecordingFamilyProfileApiService()
        viewModel = FamilyRestrictionSummaryViewModel(
            familyProfileRepository = FamilyProfileRepository(familyApi),
            authSessionStore = sessionStore,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun signedOutUserNeverCallsFamilyApi() {
        viewModel.fetchSummary()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = assertInstanceOf(
            FamilyRestrictionSummaryUiState.Error::class.java,
            viewModel.uiState.value,
        )
        assertEquals("Sign in to view family restrictions.", error.message)
        assertEquals(0, familyApi.summaryCalls)
    }

    @Test
    fun signedInUserLoadsSummaryThroughFamilyApi() {
        familyApi.summaryResponse = Response.success(
            FamilyRestrictionSumRes(
                familyMembers = listOf(
                    FamilyMeRestrictionSum(
                        userId = 14L,
                        name = "Wong",
                        isActive = true,
                        restrictions = listOf(
                            FamilyMeRestrictionDetail(
                                code = "PEANUT",
                                displayName = "Peanut",
                                severity = "AVOID",
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(sessionStore.saveSession(validSession()))

        viewModel.fetchSummary()
        testDispatcher.scheduler.advanceUntilIdle()

        val success = assertInstanceOf(
            FamilyRestrictionSummaryUiState.Success::class.java,
            viewModel.uiState.value,
        )
        assertEquals(listOf("Peanut"), success.uniqueRestrictions)
        assertEquals(1, familyApi.summaryCalls)
    }

    @Test
    fun signedInEmptyActiveMembersMapsToEmptyState() {
        familyApi.summaryResponse = Response.success(
            FamilyRestrictionSumRes(
                familyMembers = listOf(
                    FamilyMeRestrictionSum(
                        userId = 14L,
                        name = "Wong",
                        isActive = false,
                        restrictions = emptyList(),
                    ),
                ),
            ),
        )
        assertTrue(sessionStore.saveSession(validSession()))

        viewModel.fetchSummary()
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(FamilyRestrictionSummaryUiState.Empty::class.java, viewModel.uiState.value)
        assertEquals(1, familyApi.summaryCalls)
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

    private class RecordingFamilyProfileApiService : FamilyProfileApiService {
        var summaryResponse: Response<FamilyRestrictionSumRes> = Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        var summaryCalls = 0

        override suspend fun getMyFamily(): Response<FamilyMeResponse> =
            Response.error(404, "{}".toResponseBody("application/json".toMediaType()))

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

        override suspend fun getFamilyRestrictionSummary(): Response<FamilyRestrictionSumRes> {
            summaryCalls++
            return summaryResponse
        }

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
}
