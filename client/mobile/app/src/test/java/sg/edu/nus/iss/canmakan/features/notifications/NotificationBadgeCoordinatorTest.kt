package sg.edu.nus.iss.canmakan.features.notifications

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
import sg.edu.nus.iss.canmakan.features.notifications.data.NotificationsApiService
import sg.edu.nus.iss.canmakan.features.notifications.data.NotificationsRepository
import sg.edu.nus.iss.canmakan.features.notifications.data.UserNotificationResponse
import sg.edu.nus.iss.canmakan.shared.notifications.SystemNotifier
import sg.edu.nus.iss.canmakan.testing.signInTestUser
import sg.edu.nus.iss.canmakan.testing.testAuthSessionStore

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationBadgeCoordinatorTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var familyApi: FakeFamilyApi
    private lateinit var notificationsApi: FakeNotificationsApi
    private lateinit var notifier: RecordingNotifier

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        familyApi = FakeFamilyApi()
        notificationsApi = FakeNotificationsApi()
        notifier = RecordingNotifier()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun signedOutToggleRecordsAnError() {
        val coordinator = coordinator()
        dispatcher.scheduler.advanceUntilIdle()

        coordinator.setNotificationsEnabled(true)

        assertEquals(
            "Sign in before changing notification settings.",
            coordinator.notificationsEnabledError.value,
        )
        assertEquals(0, familyApi.setCalls)
    }

    @Test
    fun matchingToggleIsIgnoredAndClearRemovesError() {
        val session = testAuthSessionStore().also { it.signInTestUser() }
        val coordinator = coordinator(session)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(coordinator.notificationsEnabled.value)

        coordinator.setNotificationsEnabled(true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, familyApi.setCalls)

        familyApi.setResponse = Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        coordinator.setNotificationsEnabled(false)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            "Could not save notification setting. Check your connection and try again.",
            coordinator.notificationsEnabledError.value,
        )

        coordinator.clearNotificationsEnabledError()
        assertNull(coordinator.notificationsEnabledError.value)
    }

    @Test
    fun blankNotificationBodyUsesFallbackCopy() {
        notificationsApi.notifications = listOf(
            UserNotificationResponse(
                id = 9L,
                type = "FAMILY_INVITE_REQUEST",
                title = "Invite",
                body = "   ",
                actionToken = "tok",
                expired = false,
                read = false,
                updatedAt = null,
            ),
        )
        val session = testAuthSessionStore().also { it.signInTestUser() }
        val coordinator = coordinator(session)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("You have a new update in CanMakan.", notifier.bodies.single())
        assertTrue(coordinator.hasUnreadNotifications.value)
    }

    @Test
    fun preferenceAndListFailuresAreSwallowed() {
        familyApi.getResponse = Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        notificationsApi.throwOnList = true
        val session = testAuthSessionStore().also { it.signInTestUser() }
        val coordinator = coordinator(session)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(coordinator.notificationsEnabled.value)
        assertFalse(coordinator.hasUnreadNotifications.value)
        coordinator.refreshNotifications()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(notifier.bodies.isEmpty())
    }

    private fun coordinator(
        session: sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore = testAuthSessionStore(),
    ) = NotificationBadgeCoordinator(
        familyProfileRepository = FamilyProfileRepository(familyApi),
        notificationsRepository = NotificationsRepository(notificationsApi),
        authSessionStore = session,
        systemNotifier = notifier,
    )

    private class RecordingNotifier : SystemNotifier {
        val bodies = mutableListOf<String>()
        override fun notify(id: Int, title: String, body: String, notificationsEnabled: Boolean) {
            bodies += body
        }
    }

    private class FakeNotificationsApi : NotificationsApiService {
        var notifications: List<UserNotificationResponse> = emptyList()
        var throwOnList = false
        override suspend fun listMyNotifications(): Response<List<UserNotificationResponse>> {
            if (throwOnList) throw IllegalStateException("offline")
            return Response.success(notifications)
        }
        override suspend fun markNotificationsRead() = Response.success(Unit)
        override suspend fun deleteNotification(notificationId: Long) = Response.success(Unit)
    }

    private class FakeFamilyApi : FamilyProfileApiService {
        var getResponse: Response<NotificationPreferenceResponse> =
            Response.success(NotificationPreferenceResponse(true))
        var setResponse: Response<NotificationPreferenceResponse> =
            Response.success(NotificationPreferenceResponse(false))
        var setCalls = 0
        private val emptyError = "{}".toResponseBody("application/json".toMediaType())

        override suspend fun getMyFamily() = Response.error<FamilyMeResponse>(404, emptyError)
        override suspend fun getFamilyMembers() = Response.success(emptyList<FamilyMemberRosterItem>())
        override suspend fun createFamily(request: CreateFamilyRequestBody) =
            Response.error<FamilyMeResponse>(500, emptyError)
        override suspend fun getProfilesByFamilyId(familyId: Long) = emptyList<FamilyProfileResponse>()
        override suspend fun getActiveProfile() = Response.error<ActiveProfileResponse>(404, emptyError)
        override suspend fun setActiveProfile(request: SetActiveProfileRequestBody) =
            Response.error<ActiveProfileResponse>(500, emptyError)
        override suspend fun getNotificationPreference() = getResponse
        override suspend fun setNotificationPreference(request: SetNotificationPreferenceRequestBody): Response<NotificationPreferenceResponse> {
            setCalls++
            return setResponse
        }
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
}
