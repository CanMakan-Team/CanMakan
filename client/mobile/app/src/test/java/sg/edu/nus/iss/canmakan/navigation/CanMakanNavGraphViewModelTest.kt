package sg.edu.nus.iss.canmakan.navigation

import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
import sg.edu.nus.iss.canmakan.features.notifications.data.NotificationsApiService
import sg.edu.nus.iss.canmakan.features.notifications.data.NotificationsRepository
import sg.edu.nus.iss.canmakan.features.notifications.data.UserNotificationResponse
import sg.edu.nus.iss.canmakan.features.family.data.ClaimInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateDependantProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.DependantProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.InvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.NotificationPreferenceResponse
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.SetActiveProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.SetNotificationPreferenceRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.UserSearchResponse
import sg.edu.nus.iss.canmakan.shared.notifications.SystemNotifier

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC19 / UC8: nav graph session identity and family membership")
class CanMakanNavGraphViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var activeProfileManager: ActiveProfileManager
    private lateinit var familyApi: RecordingFamilyProfileApiService
    private lateinit var notificationsApi: FakeNotificationsApiService
    private lateinit var systemNotifier: RecordingSystemNotifier
    private lateinit var viewModel: CanMakanNavGraphViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionStore = AuthSessionStore(FakeAuthSessionPersistence(), Gson())
        activeProfileManager = ActiveProfileManager()
        familyApi = RecordingFamilyProfileApiService()
        notificationsApi = FakeNotificationsApiService()
        systemNotifier = RecordingSystemNotifier()
        viewModel = CanMakanNavGraphViewModel(
            activeProfileManager = activeProfileManager,
            dietaryRestrictionRepo = FakeDietaryRestrictionRepository(),
            familyProfileRepository = FamilyProfileRepository(familyApi),
            notificationsRepository = NotificationsRepository(notificationsApi),
            authSessionStore = sessionStore,
            systemNotifier = systemNotifier,
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
        assertTrue(viewModel.profiles.value.isEmpty())
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
    fun authenticatedUserWithoutProfileKeepsExplicitProfilelessShellState() {
        familyApi.meResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        familyApi.activeProfileResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )

        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.hasUserSession.value)
        assertFalse(viewModel.hasFamily.value)
        assertTrue(viewModel.profiles.value.isEmpty())
        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, viewModel.currentProfileId.value)
        assertNull(activeProfileManager.selection.value)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
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
        assertTrue(viewModel.profiles.value.isEmpty())
        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
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
        assertEquals(88L, activeProfileManager.currentProfileId.value)
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
        assertEquals(88L, activeProfileManager.currentProfileId.value)
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

    @Test
    fun directAccountSwitchClearsOldFamilyImmediatelyAndIgnoresBlockedReload() {
        familyApi.meResponse = Response.success(FAMILY_ME)
        familyApi.profiles = listOf(profileResponse(77L, "Old Account"))
        familyApi.activeProfileResponse = Response.success(activeResponse(77L, "Old Account"))
        familyApi.blockNextMeCall = true
        familyApi.meGate = CompletableDeferred()

        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.runCurrent()

        val otherFamily = FAMILY_ME.copy(
            familyId = 60L,
            familyName = "Other Family",
            selfProfileId = 99L,
            createdByUserId = 22L,
        )
        familyApi.meResponse = Response.success(otherFamily)
        familyApi.profiles = listOf(profileResponse(99L, "Other Account", familyId = 60L))
        familyApi.activeProfileResponse = Response.success(
            activeResponse(99L, "Other Account", familyId = 60L),
        )
        assertTrue(sessionStore.saveSession(sessionFor(22L, "other@example.com")))
        testDispatcher.scheduler.runCurrent()

        assertFalse(viewModel.hasFamily.value)
        assertTrue(viewModel.profiles.value.isEmpty())
        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)

        familyApi.meGate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.hasFamily.value)
        assertEquals("Other Family", viewModel.familyName.value)
        assertEquals(99L, viewModel.profiles.value.single().id)
        assertEquals(
            ActiveProfileManager.Selection(requireNotNull(sessionStore.accountKey.value), 99L),
            activeProfileManager.selection.value,
        )
    }

    @Test
    fun sameUserTokenReplacementDoesNotResetProfileOrReloadNavigationLifecycle() {
        familyApi.meResponse = Response.success(FAMILY_ME)
        familyApi.profiles = listOf(profileResponse(77L, "Current Account"))
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        val meCallsBeforeRefresh = familyApi.meCalls

        assertTrue(
            sessionStore.saveSession(
                validSession().copy(accessToken = "replacement-access-token"),
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(meCallsBeforeRefresh, familyApi.meCalls)
        assertEquals(
            ActiveProfileManager.Selection(requireNotNull(sessionStore.accountKey.value), 77L),
            activeProfileManager.selection.value,
        )
    }

    @Test
    fun staleSwitchProfileResultCannotOverwriteNewAccountsProfile() {
        familyApi.meResponse = Response.success(FAMILY_ME)
        familyApi.profiles = listOf(profileResponse(77L, "Old Account"))
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        familyApi.blockNextSetActiveCall = true
        familyApi.setActiveGate = CompletableDeferred()

        viewModel.switchProfile(88L)
        testDispatcher.scheduler.runCurrent()

        familyApi.meResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        familyApi.activeProfileResponse = Response.success(activeResponse(99L, "Other Account"))
        assertTrue(sessionStore.saveSession(sessionFor(22L, "other@example.com")))
        testDispatcher.scheduler.runCurrent()

        familyApi.setActiveGate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            ActiveProfileManager.Selection(requireNotNull(sessionStore.accountKey.value), 99L),
            activeProfileManager.selection.value,
        )
        assertEquals(99L, activeProfileManager.currentProfileId.value)
    }

    @Test
    fun validProfileAccountToProfilelessAccountNeverExposesOldProfile() {
        familyApi.meResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        familyApi.activeProfileResponse = Response.success(activeResponse(77L, "Old Account"))
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(77L, activeProfileManager.currentProfileId.value)

        familyApi.activeProfileResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        assertTrue(sessionStore.saveSession(sessionFor(22L, "profileless@example.com")))
        testDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.profiles.value.isEmpty())
        assertNull(activeProfileManager.selection.value)
        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, viewModel.currentProfileId.value)

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.profiles.value.isEmpty())
        assertNull(activeProfileManager.selection.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun restoredAuthenticatedSessionWithoutProfileDoesNotInventOne() {
        val persistence = FakeAuthSessionPersistence()
        val persistedStore = AuthSessionStore(persistence, Gson())
        assertTrue(persistedStore.saveSession(validSession()))

        val restoredStore = AuthSessionStore(persistence, Gson())
        val restoredManager = ActiveProfileManager()
        val restoredApi = RecordingFamilyProfileApiService().apply {
            meResponse = Response.error(
                404,
                "{}".toResponseBody("application/json".toMediaType()),
            )
            activeProfileResponse = Response.error(
                404,
                "{}".toResponseBody("application/json".toMediaType()),
            )
        }
        val restoredViewModel = CanMakanNavGraphViewModel(
            activeProfileManager = restoredManager,
            dietaryRestrictionRepo = FakeDietaryRestrictionRepository(),
            familyProfileRepository = FamilyProfileRepository(restoredApi),
            notificationsRepository = NotificationsRepository(FakeNotificationsApiService()),
            authSessionStore = restoredStore,
            systemNotifier = RecordingSystemNotifier(),
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(14L, restoredStore.accountKey.value?.userId)
        assertTrue(restoredViewModel.hasUserSession.value)
        assertTrue(restoredViewModel.profiles.value.isEmpty())
        assertNull(restoredManager.selection.value)
        assertFalse(restoredViewModel.isLoading.value)
    }

    @Test
    fun laterProfileSetupMovesProfilelessShellToValidProfileWithoutLoop() {
        familyApi.meResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        familyApi.activeProfileResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.profiles.value.isEmpty())

        val accountKey = requireNotNull(sessionStore.accountKey.value)
        activeProfileManager.switchProfile(accountKey, 42L)
        familyApi.activeProfileResponse = Response.success(activeResponse(42L, "Person"))
        viewModel.refreshRestrictions()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(42L, viewModel.currentProfileId.value)
        assertEquals(42L, viewModel.profiles.value.single().id)
        assertEquals("Person", viewModel.profiles.value.single().profileName)
        assertNull(viewModel.error.value)
    }

    @Test
    fun staleFamilyCreationCannotInvokeCallbackOrOverwriteNewAccount() {
        familyApi.meResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        familyApi.activeProfileResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        familyApi.createResponse = Response.success(201, FAMILY_ME)
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        familyApi.blockNextCreateCall = true
        familyApi.createGate = CompletableDeferred()
        var callbackCalled = false

        viewModel.createFamilyCircle("Old Family") { callbackCalled = true }
        testDispatcher.scheduler.runCurrent()

        familyApi.meResponse = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        familyApi.activeProfileResponse = Response.success(activeResponse(99L, "Other Account"))
        assertTrue(sessionStore.saveSession(sessionFor(22L, "other@example.com")))
        testDispatcher.scheduler.runCurrent()

        familyApi.createGate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(callbackCalled)
        assertEquals(
            ActiveProfileManager.Selection(requireNotNull(sessionStore.accountKey.value), 99L),
            activeProfileManager.selection.value,
        )
    }

    @Test
    fun notificationPreferenceLoadsFromServerOnAccountLoad() {
        familyApi.notificationPreferenceResponse =
            Response.success(NotificationPreferenceResponse(notificationsEnabled = false))

        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.notificationsEnabled.value)
        assertEquals(1, familyApi.getNotificationPreferenceCalls)
    }

    @Test
    fun setNotificationsEnabledUpdatesOptimisticallyThenPersists() {
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.notificationsEnabled.value)

        viewModel.setNotificationsEnabled(false)
        assertFalse(viewModel.notificationsEnabled.value)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.notificationsEnabled.value)
        assertEquals(1, familyApi.setNotificationPreferenceCalls)
        assertNull(viewModel.notificationsEnabledError.value)
    }

    @Test
    fun setNotificationsEnabledRollsBackOnFailure() {
        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()
        familyApi.setNotificationPreferenceResponse = Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaType()),
        )

        viewModel.setNotificationsEnabled(false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.notificationsEnabled.value)
        assertEquals(
            "Could not save notification setting. Check your connection and try again.",
            viewModel.notificationsEnabledError.value,
        )
    }

    @Test
    fun newUnreadNotificationPostsSystemNotificationOnce() {
        notificationsApi.notifications = listOf(
            UserNotificationResponse(
                id = 1L,
                type = "FAMILY_INVITE_REQUEST",
                title = "New invite",
                body = "You were invited to join a family.",
                actionToken = "token",
                expired = false,
                read = false,
                updatedAt = null,
            ),
        )

        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, systemNotifier.calls.size)
        assertEquals("New invite", systemNotifier.calls.single().title)

        // A later refresh of the same still-unread item must not notify again.
        viewModel.refreshNotifications()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, systemNotifier.calls.size)
    }

    @Test
    fun notificationsDisabledIsPassedThroughToSystemNotifier() {
        familyApi.notificationPreferenceResponse =
            Response.success(NotificationPreferenceResponse(notificationsEnabled = false))
        notificationsApi.notifications = listOf(
            UserNotificationResponse(
                id = 1L,
                type = "FAMILY_INVITE_REQUEST",
                title = "New invite",
                body = "You were invited to join a family.",
                actionToken = "token",
                expired = false,
                read = false,
                updatedAt = null,
            ),
        )

        assertTrue(sessionStore.saveSession(validSession()))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, systemNotifier.calls.size)
        assertFalse(systemNotifier.calls.single().notificationsEnabled)
    }

    private fun validSession(): AuthenticatedSession {
        return sessionFor(14L, "person@example.com")
    }

    private fun sessionFor(userId: Long, email: String): AuthenticatedSession {
        return AuthenticatedSession(
            accessToken = "access-token-$userId",
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(userId, email, AuthRole.USER),
        )
    }

    private fun profileResponse(
        id: Long,
        name: String,
        familyId: Long = 50L,
    ) = FamilyProfileResponse(
        id = id,
        profileName = name,
        familyId = familyId,
        relationship = "Self",
        initials = name.take(1),
        isPrimary = true,
    )

    private fun activeResponse(
        id: Long,
        name: String,
        familyId: Long? = null,
    ) = ActiveProfileResponse(
        profileId = id,
        profileName = name,
        relationship = "SELF",
        familyId = familyId,
        isPrimary = true,
    )

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

    private class FakeNotificationsApiService : NotificationsApiService {
        var notifications: List<UserNotificationResponse> = emptyList()

        override suspend fun listMyNotifications(): Response<List<UserNotificationResponse>> =
            Response.success(notifications)

        override suspend fun markNotificationsRead(): Response<Unit> = Response.success(Unit)

        override suspend fun deleteNotification(notificationId: Long): Response<Unit> =
            Response.success(Unit)
    }

    private class RecordingSystemNotifier : SystemNotifier {
        data class Call(val id: Int, val title: String, val body: String, val notificationsEnabled: Boolean)

        val calls = mutableListOf<Call>()

        override fun notify(id: Int, title: String, body: String, notificationsEnabled: Boolean) {
            calls += Call(id, title, body, notificationsEnabled)
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
        var blockNextMeCall = false
        var meGate: CompletableDeferred<Unit>? = null
        var blockNextCreateCall = false
        var createGate: CompletableDeferred<Unit>? = null
        var blockNextSetActiveCall = false
        var setActiveGate: CompletableDeferred<Unit>? = null
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
            val response = meResponse
            if (blockNextMeCall) {
                blockNextMeCall = false
                withContext(NonCancellable) { meGate?.await() }
            }
            return response
        }

        override suspend fun getFamilyMembers(): Response<List<FamilyMemberRosterItem>> =
            Response.success(emptyList())

        override suspend fun createFamily(
            request: CreateFamilyRequestBody,
        ): Response<FamilyMeResponse> {
            createCalls++
            val response = createResponse
            if (blockNextCreateCall) {
                blockNextCreateCall = false
                withContext(NonCancellable) { createGate?.await() }
            }
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
            if (blockNextSetActiveCall) {
                blockNextSetActiveCall = false
                withContext(NonCancellable) { setActiveGate?.await() }
            }
            return setActiveProfileResponse
        }

        var notificationPreferenceResponse: Response<NotificationPreferenceResponse> =
            Response.success(NotificationPreferenceResponse(notificationsEnabled = true))
        var setNotificationPreferenceResponse: Response<NotificationPreferenceResponse>? = null
        var getNotificationPreferenceCalls = 0
        var setNotificationPreferenceCalls = 0

        override suspend fun getNotificationPreference(): Response<NotificationPreferenceResponse> {
            getNotificationPreferenceCalls++
            return notificationPreferenceResponse
        }

        override suspend fun setNotificationPreference(
            request: SetNotificationPreferenceRequestBody,
        ): Response<NotificationPreferenceResponse> {
            setNotificationPreferenceCalls++
            setNotificationPreferenceResponse?.let { return it }
            val response = Response.success(
                NotificationPreferenceResponse(notificationsEnabled = request.notificationsEnabled),
            )
            notificationPreferenceResponse = response
            return response
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
