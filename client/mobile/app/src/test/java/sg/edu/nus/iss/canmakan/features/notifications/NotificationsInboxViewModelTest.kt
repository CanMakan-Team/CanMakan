package sg.edu.nus.iss.canmakan.features.notifications

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
import sg.edu.nus.iss.canmakan.features.notifications.data.NotificationsApiService
import sg.edu.nus.iss.canmakan.features.notifications.data.NotificationsException
import sg.edu.nus.iss.canmakan.features.notifications.data.NotificationsRepository
import sg.edu.nus.iss.canmakan.features.notifications.data.UserNotificationResponse

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("NotificationsInboxViewModel")
class NotificationsInboxViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var notificationsApi: RecordingNotificationsApiService
    private lateinit var familyApi: RecordingFamilyProfileApiService
    private lateinit var viewModel: NotificationsInboxViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionStore = AuthSessionStore(FakeAuthSessionPersistence(), Gson())
        notificationsApi = RecordingNotificationsApiService()
        familyApi = RecordingFamilyProfileApiService()
        viewModel = NotificationsInboxViewModel(
            notificationsRepository = NotificationsRepository(notificationsApi),
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
    fun loadsMixedAdminAndInviteeCardsWithoutMarkingThemRead() = runTest {
        notificationsApi.notifications = listOf(adminSentCard(), inviteePendingCard())
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.notifications.size)
        assertFalse(viewModel.uiState.value.notifications[0].canAcceptOrDecline)
        assertTrue(viewModel.uiState.value.notifications[1].canAcceptOrDecline)
        // Opening the inbox is just a glance; nothing is marked read until the user asks for it.
        assertEquals(0, notificationsApi.markReadCalls)
    }

    @Test
    fun markAllReadCallsEndpointAndFlipsLocalReadFlags() = runTest {
        notificationsApi.notifications = listOf(adminSentCard(), inviteePendingCard())
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        var marked = false
        viewModel.markAllRead { marked = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(marked)
        assertEquals(1, notificationsApi.markReadCalls)
        assertTrue(viewModel.uiState.value.notifications.all { it.read })
    }

    @Test
    fun acceptRemovesInviteeCardAndCallsAcceptEndpoint() = runTest {
        notificationsApi.notifications = listOf(adminSentCard(), inviteePendingCard())
        familyApi.acceptResponse = Response.success(
            FamilyMeResponse(1L, "Wong Family", "MEMBER", 9L, 10L),
        )
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        var accepted = false
        viewModel.accept("tok") { accepted = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(accepted)
        assertEquals(1, familyApi.acceptCalls)
        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertEquals("FAMILY_INVITE_UPDATE", viewModel.uiState.value.notifications[0].type)
        assertEquals("Wong Family", viewModel.uiState.value.acceptedFamilyName)
    }

    @Test
    fun familyApiFailureOnAcceptKeepsInviteeCard() = runTest {
        notificationsApi.notifications = listOf(inviteePendingCard())
        familyApi.acceptResponse = Response.error(
            409,
            """{"message":"already a member"}""".toResponseBody("application/json".toMediaType()),
        )
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.accept("tok") {}
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertEquals("already a member", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun declineRemovesInviteeCard() = runTest {
        notificationsApi.notifications = listOf(inviteePendingCard())
        familyApi.declineResponse = Response.success(Unit)
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.decline("tok")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, familyApi.declineCalls)
        assertTrue(viewModel.uiState.value.notifications.isEmpty())
    }

    @Test
    fun deleteRemovesCardWithoutDeclining() = runTest {
        notificationsApi.notifications = listOf(adminSentCard(), inviteePendingCard())
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.delete(2L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(2L), notificationsApi.deletedIds)
        assertEquals(0, familyApi.declineCalls)
        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertEquals(1L, viewModel.uiState.value.notifications[0].id)
    }

    @Test
    fun signedOutRefreshShowsSignInError() {
        viewModel.refresh()
        assertEquals("Sign in to view notifications.", viewModel.uiState.value.errorMessage)
        viewModel.accept("tok") {}
        assertEquals("Sign in to accept invitations.", viewModel.uiState.value.errorMessage)
        viewModel.decline("tok")
        assertEquals("Sign in to decline invitations.", viewModel.uiState.value.errorMessage)
        viewModel.delete(1L)
        assertEquals("Sign in to delete notifications.", viewModel.uiState.value.errorMessage)
        viewModel.markAllRead {}
        assertEquals("Sign in to update notifications.", viewModel.uiState.value.errorMessage)
        viewModel.clearError()
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun listFailureSurfacesRepositoryMessage() = runTest {
        notificationsApi.listError = NotificationsException("inbox offline", 503)
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("inbox offline", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun declineFailureKeepsInviteeCard() = runTest {
        notificationsApi.notifications = listOf(inviteePendingCard())
        familyApi.declineResponse = Response.error(
            400,
            """{"message":"already declined"}""".toResponseBody("application/json".toMediaType()),
        )
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.decline("tok")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertEquals("already declined", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun markAllReadNoopsWhenEverythingIsAlreadyRead() = runTest {
        notificationsApi.notifications = listOf(adminSentCard().copy(read = true))
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.markAllRead {}
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, notificationsApi.markReadCalls)
    }

    @Test
    fun deleteFailureReloadsInbox() = runTest {
        notificationsApi.notifications = listOf(adminSentCard())
        notificationsApi.deleteError = NotificationsException("could not delete", 500)
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.delete(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(1L), notificationsApi.deletedIds)
        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertEquals(1L, viewModel.uiState.value.notifications[0].id)
    }

    @Test
    fun markAllReadFailureKeepsUnreadState() = runTest {
        notificationsApi.notifications = listOf(adminSentCard())
        notificationsApi.markReadError = NotificationsException("mark failed", 500)
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.markAllRead {}
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("mark failed", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.notifications[0].read)
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
    }

    private fun validSession(): AuthenticatedSession {
        return AuthenticatedSession(
            accessToken = "access-token",
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(10L, "admin@example.com", AuthRole.USER),
        )
    }

    private fun adminSentCard(): UserNotificationResponse {
        return UserNotificationResponse(
            id = 1L,
            type = "FAMILY_INVITE_UPDATE",
            title = "Invite sent to jamie@example.com.",
            body = "Wong Family",
            actionToken = null,
            expired = false,
            read = false,
            updatedAt = "2026-08-14T00:00:00Z",
        )
    }

    private fun inviteePendingCard(): UserNotificationResponse {
        return UserNotificationResponse(
            id = 2L,
            type = "FAMILY_INVITE_REQUEST",
            title = "Join Wong Family?",
            body = "Invited by Amelia.",
            actionToken = "tok",
            expired = false,
            read = false,
            updatedAt = "2026-08-14T00:00:00Z",
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

    private class RecordingNotificationsApiService : NotificationsApiService {
        var notifications: List<UserNotificationResponse> = emptyList()
        var markReadCalls = 0
        val deletedIds = mutableListOf<Long>()
        var listError: NotificationsException? = null
        var deleteError: NotificationsException? = null
        var markReadError: NotificationsException? = null

        override suspend fun listMyNotifications(): Response<List<UserNotificationResponse>> {
            listError?.let { throw it }
            return Response.success(notifications)
        }

        override suspend fun markNotificationsRead(): Response<Unit> {
            markReadCalls++
            markReadError?.let { throw it }
            return Response.success(Unit)
        }

        override suspend fun deleteNotification(notificationId: Long): Response<Unit> {
            deletedIds += notificationId
            deleteError?.let { throw it }
            return Response.success(Unit)
        }
    }

    private class RecordingFamilyProfileApiService : FamilyProfileApiService {
        var acceptResponse: Response<FamilyMeResponse> = Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        var declineResponse: Response<Unit> = Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        var acceptCalls = 0
        var declineCalls = 0

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

        override suspend fun getNotificationPreference(): Response<NotificationPreferenceResponse> =
            Response.success(NotificationPreferenceResponse(notificationsEnabled = true))

        override suspend fun setNotificationPreference(
            request: SetNotificationPreferenceRequestBody,
        ): Response<NotificationPreferenceResponse> =
            Response.success(NotificationPreferenceResponse(request.notificationsEnabled))

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

        override suspend fun acceptInvitation(token: String): Response<FamilyMeResponse> {
            acceptCalls++
            return acceptResponse
        }

        override suspend fun declineInvitation(token: String): Response<Unit> {
            declineCalls++
            return declineResponse
        }

        override suspend fun createDependantProfile(
            request: CreateDependantProfileRequestBody,
        ): Response<DependantProfileResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
    }
}
