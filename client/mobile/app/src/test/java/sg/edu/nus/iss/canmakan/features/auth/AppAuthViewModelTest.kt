package sg.edu.nus.iss.canmakan.features.auth

import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.onboarding.PendingOnboardingStore
import sg.edu.nus.iss.canmakan.features.auth.session.AuthLogoutAction
import sg.edu.nus.iss.canmakan.features.auth.session.AuthRestorationResult
import sg.edu.nus.iss.canmakan.features.auth.session.AuthRestorer
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionPersistence
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC19 7.7: root authentication lifecycle")
class AppAuthViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: AuthSessionStore
    private lateinit var restorer: FakeRestorer
    private lateinit var logoutAction: FakeLogoutAction
    private lateinit var pendingOnboardingStore: PendingOnboardingStore
    private lateinit var activeProfileManager: ActiveProfileManager

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        store = AuthSessionStore(FakeSessionPersistence(), Gson())
        restorer = FakeRestorer()
        logoutAction = FakeLogoutAction(store)
        pendingOnboardingStore = PendingOnboardingStore()
        activeProfileManager = ActiveProfileManager()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsRestoringAndPersistedUserCannotBypassValidation() {
        store.saveSession(session(USER))
        restorer.results.add(AuthRestorationResult.TemporarilyUnavailable)

        val viewModel = viewModel()

        assertEquals(AppAuthState.Restoring, viewModel.state.value)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppAuthState.TemporarilyUnavailable, viewModel.state.value)
    }

    @Test
    fun restorationResultsMapToTokenSafeApplicationStates() {
        listOf(
            AuthRestorationResult.Authenticated(USER) to AppAuthState.Authenticated(USER),
            AuthRestorationResult.Authenticated(ADMIN) to
                AppAuthState.UnsupportedMobileAccount(ADMIN),
            AuthRestorationResult.Unauthenticated to AppAuthState.Unauthenticated,
            AuthRestorationResult.TemporarilyUnavailable to
                AppAuthState.TemporarilyUnavailable,
            AuthRestorationResult.Forbidden to AppAuthState.Forbidden,
        ).forEach { (result, expected) ->
            restorer.results.add(result)
            val viewModel = viewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(expected, viewModel.state.value)
        }
    }

    @Test
    fun retryRunsRestorationAgainAfterTemporaryFailure() {
        restorer.results.add(AuthRestorationResult.TemporarilyUnavailable)
        restorer.results.add(AuthRestorationResult.Authenticated(USER))
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.retryRestoration()
        assertEquals(AppAuthState.Restoring, viewModel.state.value)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppAuthState.Authenticated(USER), viewModel.state.value)
        assertEquals(2, restorer.calls)
    }

    @Test
    fun activeRestorationRejectsDuplicateRetryRequests() {
        restorer.gate = CompletableDeferred()
        restorer.results.add(AuthRestorationResult.Unauthenticated)
        val viewModel = viewModel()
        testDispatcher.scheduler.runCurrent()

        viewModel.retryRestoration()
        viewModel.retryRestoration()

        assertEquals(1, restorer.calls)
        restorer.gate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppAuthState.Unauthenticated, viewModel.state.value)
    }

    @Test
    fun loginSuccessAllowsUserButNeverMapsAdminIntoConsumerMain() {
        restorer.results.add(AuthRestorationResult.Unauthenticated)
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onLoginSuccess(USER)
        assertEquals(AppAuthState.Authenticated(USER), viewModel.state.value)

        viewModel.onLoginSuccess(ADMIN)
        assertEquals(AppAuthState.UnsupportedMobileAccount(ADMIN), viewModel.state.value)
    }

    @Test
    fun asynchronousSessionClearRemovesConsumerMainEligibility() {
        store.saveSession(session(USER))
        restorer.results.add(AuthRestorationResult.Authenticated(USER))
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertInstanceOf(AppAuthState.Authenticated::class.java, viewModel.state.value)
        activeProfileManager.switchProfile(requireNotNull(store.accountKey.value), 77L)
        pendingOnboardingStore.requestDietarySetup("Person", USER.email)

        store.clearSession()
        testDispatcher.scheduler.runCurrent()

        assertEquals(AppAuthState.Unauthenticated, viewModel.state.value)
        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertNull(pendingOnboardingStore.peek())
    }

    @Test
    fun backendAuthoritativeUserToAdminChangeLeavesConsumerMain() {
        store.saveSession(session(USER))
        restorer.results.add(AuthRestorationResult.Authenticated(USER))
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(store.updateAuthenticatedUser(ADMIN))
        testDispatcher.scheduler.runCurrent()

        assertEquals(AppAuthState.UnsupportedMobileAccount(ADMIN), viewModel.state.value)
    }

    @Test
    fun signingOutBlocksLoginUntilCleanupCompletesAndPreventsDuplicateLogout() {
        store.saveSession(session(USER))
        restorer.results.add(AuthRestorationResult.Authenticated(USER))
        logoutAction.gate = CompletableDeferred()
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.signOut()
        viewModel.signOut()
        viewModel.onLoginSuccess(USER)
        testDispatcher.scheduler.runCurrent()

        assertEquals(AppAuthState.SigningOut, viewModel.state.value)
        assertEquals(1, logoutAction.calls)
        logoutAction.gate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppAuthState.Unauthenticated, viewModel.state.value)
        assertNull(store.authenticatedUser.value)
    }

    @Test
    fun logoutFailureStillEndsInUnauthenticatedAfterLocalClear() {
        store.saveSession(session(ADMIN))
        restorer.results.add(AuthRestorationResult.Authenticated(ADMIN))
        logoutAction.failure = IllegalStateException("sensitive backend detail")
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppAuthState.Unauthenticated, viewModel.state.value)
        assertNull(store.currentAccessToken())
        assertFalse(viewModel.toString().contains("sensitive backend detail"))
    }

    @Test
    fun exposedRootStateContainsOnlySafeUserMetadata() {
        store.saveSession(session(USER))
        restorer.results.add(AuthRestorationResult.Authenticated(USER))
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.toString().contains(TEST_ACCESS_TOKEN))
        assertFalse(viewModel.toString().contains(TEST_ACCESS_TOKEN))
        assertFalse(
            AppAuthState.Authenticated::class.java.declaredFields.any { field ->
                field.name.contains("token", ignoreCase = true) ||
                    field.name.contains("cookie", ignoreCase = true) ||
                    field.name.contains("password", ignoreCase = true)
            }
        )
    }

    @Test
    fun logoutClearsPendingOnboardingAndResetsActiveProfile() {
        store.saveSession(session(USER))
        restorer.results.add(AuthRestorationResult.Authenticated(USER))
        val viewModel = viewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        activeProfileManager.switchProfile(requireNotNull(store.accountKey.value), 77L)
        pendingOnboardingStore.requestDietarySetup("Person", USER.email)

        viewModel.signOut()

        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertNull(pendingOnboardingStore.peek())
    }

    @Test
    fun authenticatedAccountChangeResetsOldActiveProfileAndRejectsOldOnboarding() {
        store.saveSession(session(USER))
        restorer.results.add(AuthRestorationResult.Authenticated(USER))
        viewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        activeProfileManager.switchProfile(requireNotNull(store.accountKey.value), 77L)
        pendingOnboardingStore.requestDietarySetup("Old Person", USER.email)

        store.saveSession(session(ADMIN))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertNull(pendingOnboardingStore.peek())
    }

    private fun viewModel(): AppAuthViewModel {
        return AppAuthViewModel(
            restorer,
            store,
            logoutAction,
            pendingOnboardingStore,
            activeProfileManager,
            testDispatcher,
        )
    }

    private class FakeRestorer : AuthRestorer {
        val results = ArrayDeque<AuthRestorationResult>()
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0

        override suspend fun restore(): AuthRestorationResult {
            calls++
            gate?.await()
            return results.removeFirst()
        }
    }

    private class FakeLogoutAction(
        private val store: AuthSessionStore,
    ) : AuthLogoutAction {
        var gate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        var calls = 0

        override suspend fun logout() {
            calls++
            gate?.await()
            store.clearSession()
            failure?.let { throw it }
        }
    }

    private class FakeSessionPersistence : AuthSessionPersistence {
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

    private companion object {
        const val TEST_ACCESS_TOKEN = "test-access-token"
        val USER = AuthenticatedUser(12L, "user@example.com", AuthRole.USER)
        val ADMIN = AuthenticatedUser(99L, "admin@example.com", AuthRole.ADMIN)

        fun session(user: AuthenticatedUser): AuthenticatedSession {
            return AuthenticatedSession(TEST_ACCESS_TOKEN, "Bearer", 900, user)
        }
    }
}
