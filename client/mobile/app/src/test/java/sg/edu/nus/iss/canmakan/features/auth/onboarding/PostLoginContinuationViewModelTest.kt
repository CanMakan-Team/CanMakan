package sg.edu.nus.iss.canmakan.features.auth.onboarding

import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionPersistence
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.family.data.FamilyApiException
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationStore

@OptIn(ExperimentalCoroutinesApi::class)
class PostLoginContinuationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var pendingOnboardingStore: PendingOnboardingStore
    private lateinit var pendingInvitationStore: PendingInvitationStore
    private lateinit var claimer: FakeInvitationClaimer
    private lateinit var viewModel: PostLoginContinuationViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessionStore = AuthSessionStore(FakeSessionPersistence(), Gson()).also {
            it.saveSession(
                AuthenticatedSession(
                    accessToken = "access-token",
                    tokenType = "Bearer",
                    expiresIn = 900,
                    user = AuthenticatedUser(14L, "person@example.com", AuthRole.USER),
                ),
            )
        }
        pendingOnboardingStore = PendingOnboardingStore()
        pendingInvitationStore = PendingInvitationStore()
        claimer = FakeInvitationClaimer()
        viewModel = PostLoginContinuationViewModel(
            sessionStore,
            pendingOnboardingStore,
            pendingInvitationStore,
            claimer,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onboardingFailureThenDeferContinuesToInvitationClaim() {
        pendingOnboardingStore.requestDietarySetup("person@example.com")
        pendingInvitationStore.offer("invite-token")

        viewModel.begin()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PostLoginContinuationState.DietarySetupRequired, viewModel.state.value)
        assertEquals(0, claimer.calls)

        // A failed setup leaves the account-bound intent in place and cannot continue yet.
        viewModel.onDietarySetupResolved()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, claimer.calls)

        // Explicit defer clears that intent, after which the same owner claims the invitation.
        pendingOnboardingStore.clear()
        viewModel.onDietarySetupResolved()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, claimer.calls)
        assertNull(pendingInvitationStore.peek())
        assertEquals(PostLoginContinuationState.Ready(), viewModel.state.value)
    }

    @Test
    fun invitationWithoutDietarySetupIsClaimedExactlyOnce() {
        pendingInvitationStore.offer("invite-token")
        claimer.gate = CompletableDeferred()

        viewModel.begin()
        viewModel.begin()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, claimer.calls)
        assertEquals(PostLoginContinuationState.ClaimingInvitation, viewModel.state.value)
        claimer.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(PostLoginContinuationState.Ready(), viewModel.state.value)
    }

    @Test
    fun claimFailureKeepsTokenAndRetrySucceedsWithoutLoggingOut() {
        pendingInvitationStore.offer("invite-token")
        claimer.failure = java.io.IOException("offline")

        viewModel.begin()
        dispatcher.scheduler.advanceUntilIdle()

        val failedState = viewModel.state.value as PostLoginContinuationState.Ready
        assertNotNull(failedState.invitationError)
        assertEquals("invite-token", pendingInvitationStore.peek())
        assertEquals("access-token", sessionStore.currentAccessToken())

        claimer.failure = null
        viewModel.retryInvitationClaim()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, claimer.calls)
        assertNull(pendingInvitationStore.peek())
        assertEquals(PostLoginContinuationState.Ready(), viewModel.state.value)
        assertEquals("access-token", sessionStore.currentAccessToken())
    }

    @Test
    fun familyApiClaimFailureMapsStatusToInvitationError() {
        pendingInvitationStore.offer("invite-token")
        claimer.failure = FamilyApiException("mismatch", 403)

        viewModel.begin()
        dispatcher.scheduler.advanceUntilIdle()

        val failedState = viewModel.state.value as PostLoginContinuationState.Ready
        assertEquals("This invitation does not match the signed-in account.", failedState.invitationError)
        assertEquals("invite-token", pendingInvitationStore.peek())
    }

    @Test
    fun noPendingWorkNavigatesDirectlyToConsumerShell() {
        viewModel.begin()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PostLoginContinuationState.Ready(), viewModel.state.value)
        assertEquals(0, claimer.calls)
    }

    @Test
    fun newDeepLinkWhileConsumerShellIsReadyUsesTheSameClaimer() {
        viewModel.begin()
        dispatcher.scheduler.advanceUntilIdle()

        pendingInvitationStore.offer("later-token")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, claimer.calls)
        assertNull(pendingInvitationStore.peek())
        assertEquals(PostLoginContinuationState.Ready(), viewModel.state.value)
    }

    @Test
    fun invitationIsNeverClaimedBeforeSessionExists() {
        sessionStore.clearSession()
        pendingInvitationStore.offer("invite-token")

        viewModel.begin()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PostLoginContinuationState.Checking, viewModel.state.value)
        assertEquals(0, claimer.calls)
        assertTrue(pendingInvitationStore.peek() != null)
    }

    @Test
    fun consumerShellCanRequestDietarySetupLater() {
        viewModel.begin()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDietarySetup()

        assertEquals(PostLoginContinuationState.DietarySetupRequired, viewModel.state.value)
        assertEquals("person@example.com", pendingOnboardingStore.peek()?.accountEmail)
    }

    @Test
    fun anotherAccountCannotConsumeRegisteredAccountsOnboarding() {
        pendingOnboardingStore.requestDietarySetup("owner@example.com")

        viewModel.begin()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PostLoginContinuationState.Ready(), viewModel.state.value)
        assertNull(pendingOnboardingStore.peek())
    }

    @Test
    fun logoutWhileInvitationClaimIsActiveKeepsTokenAndInvalidatesContinuation() {
        pendingInvitationStore.offer("invite-token")
        claimer.gate = CompletableDeferred()
        viewModel.begin()
        dispatcher.scheduler.runCurrent()

        sessionStore.clearSession()
        dispatcher.scheduler.runCurrent()

        assertEquals(PostLoginContinuationState.Checking, viewModel.state.value)
        assertEquals("invite-token", pendingInvitationStore.peek())
    }

    @Test
    fun directAccountSwitchStartsNewContinuationAndIgnoresOldClaimResult() {
        pendingInvitationStore.offer("invite-token")
        claimer.gate = CompletableDeferred()
        claimer.ignoreCancellation = true
        viewModel.begin()
        dispatcher.scheduler.runCurrent()

        sessionStore.saveSession(sessionFor(22L, "other@example.com"))
        viewModel.begin()
        dispatcher.scheduler.runCurrent()

        assertEquals(2, claimer.calls)
        assertEquals(PostLoginContinuationState.ClaimingInvitation, viewModel.state.value)

        claimer.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(pendingInvitationStore.peek())
        assertEquals(PostLoginContinuationState.Ready(), viewModel.state.value)
    }

    @Test
    fun oldClaimCannotClearNewerTokenForSameAccount() {
        pendingInvitationStore.offer("first-token")
        claimer.gate = CompletableDeferred()
        viewModel.begin()
        dispatcher.scheduler.runCurrent()

        pendingInvitationStore.offer("second-token")
        dispatcher.scheduler.runCurrent()
        claimer.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("first-token", "second-token"), claimer.claimedTokens)
        assertNull(pendingInvitationStore.peek())
        assertEquals(PostLoginContinuationState.Ready(), viewModel.state.value)
    }

    private class FakeInvitationClaimer : PendingInvitationClaimer {
        var calls = 0
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var ignoreCancellation = false
        val claimedTokens = mutableListOf<String>()

        override suspend fun claim(invitationToken: String) {
            calls++
            claimedTokens += invitationToken
            if (ignoreCancellation) {
                withContext(NonCancellable) { gate?.await() }
            } else {
                gate?.await()
            }
            failure?.let { throw it }
        }
    }

    private class FakeSessionPersistence : AuthSessionPersistence {
        private var value: String? = null

        override fun readSession(): String? = value
        override fun writeSession(serializedSession: String): Boolean {
            value = serializedSession
            return true
        }
        override fun clearSession(): Boolean {
            value = null
            return true
        }
    }

    private companion object {
        fun sessionFor(userId: Long, email: String) = AuthenticatedSession(
            accessToken = "access-token-$userId",
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(userId, email, AuthRole.USER),
        )
    }
}
