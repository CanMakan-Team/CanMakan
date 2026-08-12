package sg.edu.nus.iss.canmakan.features.dietaryprofile.setup

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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.onboarding.PendingOnboardingStore
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionPersistence
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.ExistingSelfProfileResolver
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.ProfileRestrictionSeverity
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.SelfProfileRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.SelfProfileResponse
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.SelfProfileSetupResult
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatedDietaryOnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var sessionPersistence: FakeSessionPersistence
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var pendingStore: PendingOnboardingStore
    private lateinit var dietaryRepository: FakeDietaryRepository
    private lateinit var selfProfileRepository: FakeSelfProfileRepository
    private lateinit var existingResolver: FakeExistingProfileResolver
    private lateinit var activeProfileManager: ActiveProfileManager
    private lateinit var viewModel: AuthenticatedDietaryOnboardingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessionPersistence = FakeSessionPersistence()
        sessionStore = AuthSessionStore(sessionPersistence, Gson())
        sessionStore.saveSession(validSession())
        pendingStore = PendingOnboardingStore().also {
            it.requestDietarySetup("person@example.com")
        }
        dietaryRepository = FakeDietaryRepository()
        selfProfileRepository = FakeSelfProfileRepository()
        existingResolver = FakeExistingProfileResolver()
        activeProfileManager = ActiveProfileManager()
        viewModel = newViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun catalogLoadsOnlyAfterAuthenticatedSetupBegins() {
        assertEquals(0, dietaryRepository.catalogCalls)

        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, dietaryRepository.catalogCalls)
        assertEquals(3, viewModel.uiState.value.restrictions.size)
        assertEquals("", viewModel.uiState.value.profileName)
    }

    @Test
    fun catalogDoesNotLoadWithoutAValidSession() {
        sessionStore.clearSession()
        viewModel = newViewModel()

        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, dietaryRepository.catalogCalls)
        assertEquals(
            AuthenticatedDietaryOnboardingViewModel.SESSION_REQUIRED_MESSAGE,
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun successfulSetupUsesCatalogIdsSwitchesProfileAndKeepsSession() {
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Person Name")
        viewModel.toggleRestriction(2L)
        viewModel.setSeverity(2L, ProfileRestrictionSeverity.INTOLERANCE)

        viewModel.createProfile()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Person Name", selfProfileRepository.lastProfileName)
        assertEquals(
            mapOf(2L to ProfileRestrictionSeverity.INTOLERANCE),
            selfProfileRepository.lastRestrictions,
        )
        assertEquals(77L, activeProfileManager.currentProfileId.value)
        assertNull(pendingStore.peek())
        assertEquals(TEST_ACCESS_TOKEN, sessionStore.currentAccessToken())
        assertTrue(viewModel.uiState.value.resolved)
    }

    @Test
    fun setupFailureKeepsIntentSessionAndUnsetActiveProfile() {
        selfProfileRepository.result = SelfProfileSetupResult.Failure("Try again")
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Person Name")
        viewModel.toggleRestriction(2L)

        viewModel.createProfile()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Try again", viewModel.uiState.value.errorMessage)
        assertEquals("person@example.com", pendingStore.peek()?.accountEmail)
        assertEquals(TEST_ACCESS_TOKEN, sessionStore.currentAccessToken())
        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertFalse(viewModel.uiState.value.resolved)
    }

    @Test
    fun conflictResolvesExistingActiveProfileInsteadOfRetryingCreate() {
        selfProfileRepository.result = SelfProfileSetupResult.AlreadyExists
        existingResolver.profileId = 88L
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Person Name")
        viewModel.toggleRestriction(2L)

        viewModel.createProfile()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, selfProfileRepository.calls)
        assertEquals(1, existingResolver.calls)
        assertEquals(1, dietaryRepository.saveCalls)
        assertEquals(88L, dietaryRepository.lastSavedProfileId)
        assertEquals(mapOf(2L to "STRICT_AVOID"), dietaryRepository.lastSavedSelections)
        assertEquals(88L, activeProfileManager.currentProfileId.value)
        assertNull(pendingStore.peek())
        assertTrue(viewModel.uiState.value.resolved)
    }

    @Test
    fun unresolvedConflictKeepsPendingStateAndDoesNotInventProfileId() {
        selfProfileRepository.result = SelfProfileSetupResult.AlreadyExists
        existingResolver.failure = IllegalStateException("not resolvable")
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Person Name")
        viewModel.toggleRestriction(2L)

        viewModel.createProfile()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertEquals("person@example.com", pendingStore.peek()?.accountEmail)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("could not be resolved") == true)
    }

    @Test
    fun explicitDeferClearsOnboardingButLeavesSessionUntouched() {
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.deferSetup()

        assertNull(pendingStore.peek())
        assertEquals(TEST_ACCESS_TOKEN, sessionStore.currentAccessToken())
        assertTrue(viewModel.uiState.value.resolved)
    }

    @Test
    fun failedRestrictionSaveAfterConflictKeepsPendingAndDoesNotSwitchProfile() {
        selfProfileRepository.result = SelfProfileSetupResult.AlreadyExists
        existingResolver.profileId = 88L
        dietaryRepository.saveResult = false
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Person Name")
        viewModel.toggleRestriction(2L)

        viewModel.createProfile()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertEquals("person@example.com", pendingStore.peek()?.accountEmail)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("could not be saved") == true)
    }

    @Test
    fun emptySelectionDoesNotCreateAnEmptyProfile() {
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Person Name")

        viewModel.createProfile()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, selfProfileRepository.calls)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("Select at least one") == true)
    }

    @Test
    fun laterSetupAcceptsAnExplicitProfileNameBeforeCreate() {
        pendingStore.requestDietarySetup("person@example.com")
        viewModel = newViewModel()
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("  Later Name  ")
        viewModel.toggleRestriction(3L)

        viewModel.createProfile()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Later Name", selfProfileRepository.lastProfileName)
        assertEquals(77L, activeProfileManager.currentProfileId.value)
        assertNull(pendingStore.peek())
    }

    @Test
    fun profileNameLength100IsAcceptedAnd101IsBlocked() {
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleRestriction(2L)
        viewModel.updateProfileName("a".repeat(100))

        viewModel.createProfile()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, selfProfileRepository.calls)

        pendingStore.requestDietarySetup("person@example.com")
        viewModel = newViewModel()
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Person Name")
        viewModel.toggleRestriction(2L)
        viewModel.updateProfileName("b".repeat(101))

        viewModel.createProfile()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, selfProfileRepository.calls)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("100") == true)
    }

    @Test
    fun logoutWhileCreationIsInFlightCannotSwitchProfile() {
        selfProfileRepository.gate = CompletableDeferred()
        selfProfileRepository.ignoreCancellation = true
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Person Name")
        viewModel.toggleRestriction(2L)
        viewModel.createProfile()
        dispatcher.scheduler.runCurrent()

        sessionStore.clearSession()
        dispatcher.scheduler.runCurrent()
        selfProfileRepository.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertNull(pendingStore.peek())
    }

    @Test
    fun staleCreationResultCannotClearNewAccountsOnboarding() {
        selfProfileRepository.gate = CompletableDeferred()
        selfProfileRepository.ignoreCancellation = true
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Person Name")
        viewModel.toggleRestriction(2L)
        viewModel.createProfile()
        dispatcher.scheduler.runCurrent()

        sessionStore.saveSession(sessionFor(22L, "other@example.com"))
        pendingStore.requestDietarySetup("other@example.com")
        dispatcher.scheduler.runCurrent()
        selfProfileRepository.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertEquals("other@example.com", pendingStore.peekForAccount("other@example.com")?.accountEmail)
    }

    @Test
    fun staleCreationCannotClearOrSwitchPastNewerSameAccountIntent() {
        selfProfileRepository.gate = CompletableDeferred()
        selfProfileRepository.ignoreCancellation = true
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Person Name")
        viewModel.toggleRestriction(2L)
        viewModel.createProfile()
        dispatcher.scheduler.runCurrent()

        val oldRequestId = requireNotNull(pendingStore.peek()).requestId
        pendingStore.requestDietarySetup("person@example.com")
        selfProfileRepository.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertTrue(pendingStore.peekForAccount("person@example.com")!!.requestId != oldRequestId)
    }

    @Test
    fun accountChangeDuring409ResolutionPreventsRestrictionSaveAndSwitch() {
        selfProfileRepository.result = SelfProfileSetupResult.AlreadyExists
        existingResolver.gate = CompletableDeferred()
        existingResolver.ignoreCancellation = true
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateProfileName("Person Name")
        viewModel.toggleRestriction(2L)
        viewModel.createProfile()
        dispatcher.scheduler.runCurrent()

        sessionStore.saveSession(sessionFor(22L, "other@example.com"))
        pendingStore.requestDietarySetup("other@example.com")
        dispatcher.scheduler.runCurrent()
        existingResolver.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, dietaryRepository.saveCalls)
        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertEquals("other@example.com", pendingStore.peek()?.accountEmail)
    }

    private fun newViewModel() = AuthenticatedDietaryOnboardingViewModel(
        authSessionStore = sessionStore,
        pendingOnboardingStore = pendingStore,
        dietaryRestrictionRepository = dietaryRepository,
        selfProfileRepository = selfProfileRepository,
        existingSelfProfileResolver = existingResolver,
        activeProfileManager = activeProfileManager,
    )

    private class FakeDietaryRepository : DietaryRestrictionRepository {
        var catalogCalls = 0
        var saveCalls = 0
        var saveResult = true
        var lastSavedProfileId: Long? = null
        var lastSavedSelections: Map<Long, String>? = null

        override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> {
            catalogCalls++
            return listOf(
                DietaryRestriction(1L, "HALAL", "Halal", "RELIGIOUS"),
                DietaryRestriction(2L, "PEANUT", "Peanut", "ALLERGEN"),
                DietaryRestriction(3L, "VEGAN", "Vegan", "DIET"),
            )
        }

        override suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String> =
            emptyMap()

        override suspend fun saveDietaryRestrictionSelections(
            profileId: Long,
            selections: Map<Long, String>,
        ): Boolean {
            saveCalls++
            lastSavedProfileId = profileId
            lastSavedSelections = selections
            return saveResult
        }
    }

    private class FakeSelfProfileRepository : SelfProfileRepository {
        var result: SelfProfileSetupResult = SelfProfileSetupResult.Created(
            SelfProfileResponse(77L, "Person Name", "SELF", true, emptyMap()),
        )
        var calls = 0
        var lastProfileName: String? = null
        var lastRestrictions: Map<Long, ProfileRestrictionSeverity>? = null
        var gate: CompletableDeferred<Unit>? = null
        var ignoreCancellation = false

        override suspend fun createSelfProfile(
            profileName: String,
            restrictions: Map<Long, ProfileRestrictionSeverity>,
        ): SelfProfileSetupResult {
            calls++
            lastProfileName = profileName
            lastRestrictions = restrictions
            if (ignoreCancellation) {
                withContext(NonCancellable) { gate?.await() }
            } else {
                gate?.await()
            }
            return result
        }
    }

    private class FakeExistingProfileResolver : ExistingSelfProfileResolver {
        var profileId = 88L
        var calls = 0
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var ignoreCancellation = false

        override suspend fun resolveActiveSelfProfileId(): Long {
            calls++
            if (ignoreCancellation) {
                withContext(NonCancellable) { gate?.await() }
            } else {
                gate?.await()
            }
            failure?.let { throw it }
            return profileId
        }
    }

    private class FakeSessionPersistence : AuthSessionPersistence {
        var value: String? = null

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
        const val TEST_ACCESS_TOKEN = "access-token"

        fun validSession() = AuthenticatedSession(
            accessToken = TEST_ACCESS_TOKEN,
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(14L, "person@example.com", AuthRole.USER),
        )

        fun sessionFor(userId: Long, email: String) = AuthenticatedSession(
            accessToken = "token-$userId",
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(userId, email, AuthRole.USER),
        )
    }
}
