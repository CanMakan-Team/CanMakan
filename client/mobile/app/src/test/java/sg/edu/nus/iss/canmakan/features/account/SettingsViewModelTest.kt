package sg.edu.nus.iss.canmakan.features.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.data.AuthFailureType
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRepository
import sg.edu.nus.iss.canmakan.features.auth.data.AuthResult
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.notifications.NotificationBadgeCoordinator
import sg.edu.nus.iss.canmakan.features.notifications.data.NotificationsApiService
import sg.edu.nus.iss.canmakan.features.notifications.data.NotificationsRepository
import sg.edu.nus.iss.canmakan.features.notifications.data.UserNotificationResponse
import sg.edu.nus.iss.canmakan.shared.notifications.SystemNotifier
import sg.edu.nus.iss.canmakan.testing.testAuthSessionStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyRestrictionSumRes
import sg.edu.nus.iss.canmakan.features.family.data.InvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.NotificationPreferenceResponse
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.SetActiveProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.SetNotificationPreferenceRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.UserSearchResponse

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SettingsViewModel delete account")
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAuthRepository
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeAuthRepository()
        viewModel = SettingsViewModel(
            repository,
            NotificationBadgeCoordinator(
                familyProfileRepository = FamilyProfileRepository(UnusedFamilyApi()),
                notificationsRepository = NotificationsRepository(EmptyNotificationsApi()),
                authSessionStore = testAuthSessionStore(),
                systemNotifier = NoOpSystemNotifier(),
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun successCallsSignOutAndDoesNotTakeAProfileId() {
        var signedOut = false
        viewModel.deleteOwnAccount(onSuccess = { signedOut = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.deleteCalls)
        assertTrue(signedOut)
        assertNull(viewModel.deleteAccountError.value)
    }

    @Test
    fun conflictShowsFamilyAdminMessageAndDoesNotSignOut() {
        repository.result = AuthResult.Failure(AuthFailureType.CONFLICT)
        var signedOut = false

        viewModel.deleteOwnAccount(onSuccess = { signedOut = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(signedOut)
        assertFalse(viewModel.isDeletingAccount.value)
        assertEquals(
            SettingsViewModel.LAST_FAMILY_ADMIN_MESSAGE,
            viewModel.deleteAccountError.value,
        )
    }

    @Test
    fun networkFailureKeepsSession() {
        repository.result = AuthResult.Failure(AuthFailureType.NETWORK)
        var signedOut = false

        viewModel.deleteOwnAccount(onSuccess = { signedOut = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(signedOut)
        assertEquals(SettingsViewModel.NETWORK_MESSAGE, viewModel.deleteAccountError.value)
    }

    @Test
    fun genericFailureUsesFallbackMessage() {
        repository.result = AuthResult.Failure(AuthFailureType.SERVER)
        viewModel.deleteOwnAccount(onSuccess = {})
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SettingsViewModel.GENERIC_MESSAGE, viewModel.deleteAccountError.value)
    }

    @Test
    fun secondDeleteWhileInFlightIsIgnored() {
        repository.gate = CompletableDeferred()
        var successCount = 0
        viewModel.deleteOwnAccount(onSuccess = { successCount++ })
        testDispatcher.scheduler.runCurrent()
        viewModel.deleteOwnAccount(onSuccess = { successCount++ })

        assertEquals(1, repository.deleteCalls)
        repository.gate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, successCount)
    }

    @Test
    fun notificationPreferenceDelegatesToCoordinator() {
        viewModel.setNotificationsEnabled(true)
        viewModel.clearNotificationsEnabledError()
        assertFalse(viewModel.notificationsEnabled.value)
        assertNull(viewModel.notificationsEnabledError.value)
    }

    private class FakeAuthRepository : AuthRepository {
        var result: AuthResult<Unit> = AuthResult.Success(Unit)
        var deleteCalls = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun login(
            email: String,
            password: String,
        ): AuthResult<AuthenticatedSession> = error("unused")

        override suspend fun getCurrentUser(): AuthResult<AuthenticatedUser> = error("unused")

        override suspend fun deleteOwnAccount(): AuthResult<Unit> {
            deleteCalls++
            gate?.await()
            return result
        }
    }

    private class NoOpSystemNotifier : SystemNotifier {
        override fun notify(id: Int, title: String, body: String, notificationsEnabled: Boolean) = Unit
    }

    private class EmptyNotificationsApi : NotificationsApiService {
        override suspend fun listMyNotifications() =
            Response.success(emptyList<UserNotificationResponse>())

        override suspend fun markNotificationsRead() = Response.success(Unit)

        override suspend fun deleteNotification(notificationId: Long) = Response.success(Unit)
    }

    private class UnusedFamilyApi : FamilyProfileApiService {
        private val emptyError = Response.error<FamilyMeResponse>(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )

        override suspend fun getMyFamily() = emptyError
        override suspend fun getFamilyMembers() = Response.success(emptyList<FamilyMemberRosterItem>())
        override suspend fun createFamily(request: CreateFamilyRequestBody) = emptyError
        override suspend fun getProfilesByFamilyId(familyId: Long) = emptyList<FamilyProfileResponse>()
        override suspend fun getActiveProfile() =
            Response.error<ActiveProfileResponse>(404, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun setActiveProfile(request: SetActiveProfileRequestBody) =
            Response.error<ActiveProfileResponse>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun getNotificationPreference() =
            Response.success(NotificationPreferenceResponse(false))
        override suspend fun setNotificationPreference(request: SetNotificationPreferenceRequestBody) =
            Response.success(NotificationPreferenceResponse(request.notificationsEnabled))
        override suspend fun getFamilyRestrictionSummary() =
            Response.error<FamilyRestrictionSumRes>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun searchUserByEmail(email: String) =
            Response.error<UserSearchResponse>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun createInvitation(request: CreateInvitationRequestBody) =
            Response.error<InvitationResponse>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun claimInvitation(request: ClaimInvitationRequestBody) = emptyError
        override suspend fun listMyInvitations() = Response.success(emptyList<PendingInvitationResponse>())
        override suspend fun acceptInvitation(token: String) = emptyError
        override suspend fun declineInvitation(token: String) = Response.error<Unit>(500, "{}".toResponseBody("application/json".toMediaType()))
        override suspend fun createDependantProfile(request: CreateDependantProfileRequestBody) =
            Response.error<DependantProfileResponse>(500, "{}".toResponseBody("application/json".toMediaType()))
    }
}
