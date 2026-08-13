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
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager

/**
 * Registration always creates the account's linked SELF profile up front, so this screen
 * only ever resolves that existing profile id and calls the same [DietaryRestrictionRepository]
 * methods [sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.DietaryRestrictionViewModel]
 * (backing DietaryRestrictionSheet) uses — no self-profile creation call is involved here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatedDietaryOnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var sessionPersistence: FakeSessionPersistence
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var pendingStore: PendingOnboardingStore
    private lateinit var dietaryRepository: FakeDietaryRepository
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
        assertEquals(1, existingResolver.calls)
        assertEquals(3, viewModel.uiState.value.restrictions.size)
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
    fun profileResolutionFailureSurfacesLoadErrorAndSkipsCatalogFetch() {
        existingResolver.failure = IllegalStateException("not resolvable")
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, dietaryRepository.catalogCalls)
        assertTrue(
            viewModel.uiState.value.errorMessage?.contains("Unable to load dietary options") == true,
        )
    }

    @Test
    fun successfulSaveUsesResolvedProfileIdSwitchesProfileAndKeepsSession() {
        existingResolver.profileId = 88L
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleRestriction(2L)
        viewModel.setSeverity(2L, ProfileRestrictionSeverity.INTOLERANCE)

        viewModel.saveRestrictions()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, dietaryRepository.saveCalls)
        assertEquals(88L, dietaryRepository.lastSavedProfileId)
        assertEquals(mapOf(2L to "INTOLERANCE"), dietaryRepository.lastSavedSelections)
        assertEquals(88L, activeProfileManager.currentProfileId.value)
        assertNull(pendingStore.peek())
        assertEquals(TEST_ACCESS_TOKEN, sessionStore.currentAccessToken())
        assertTrue(viewModel.uiState.value.resolved)
    }

    @Test
    fun saveFailureKeepsIntentSessionAndUnsetActiveProfile() {
        dietaryRepository.saveResult = false
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleRestriction(2L)

        viewModel.saveRestrictions()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errorMessage?.contains("Unable to save") == true)
        assertEquals("person@example.com", pendingStore.peek()?.accountEmail)
        assertEquals(TEST_ACCESS_TOKEN, sessionStore.currentAccessToken())
        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertFalse(viewModel.uiState.value.resolved)
    }

    @Test
    fun emptySelectionDoesNotSave() {
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveRestrictions()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, dietaryRepository.saveCalls)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("Select at least one") == true)
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
    fun logoutWhileSavingIsInFlightCannotSwitchProfile() {
        dietaryRepository.gate = CompletableDeferred()
        dietaryRepository.ignoreCancellation = true
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleRestriction(2L)
        viewModel.saveRestrictions()
        dispatcher.scheduler.runCurrent()

        sessionStore.clearSession()
        dispatcher.scheduler.runCurrent()
        dietaryRepository.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertNull(pendingStore.peek())
    }

    @Test
    fun staleSaveResultCannotClearNewAccountsOnboarding() {
        dietaryRepository.gate = CompletableDeferred()
        dietaryRepository.ignoreCancellation = true
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleRestriction(2L)
        viewModel.saveRestrictions()
        dispatcher.scheduler.runCurrent()

        sessionStore.saveSession(sessionFor(22L, "other@example.com"))
        pendingStore.requestDietarySetup("other@example.com")
        dispatcher.scheduler.runCurrent()
        dietaryRepository.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertEquals("other@example.com", pendingStore.peekForAccount("other@example.com")?.accountEmail)
    }

    @Test
    fun staleSaveCannotClearOrSwitchPastNewerSameAccountIntent() {
        dietaryRepository.gate = CompletableDeferred()
        dietaryRepository.ignoreCancellation = true
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleRestriction(2L)
        viewModel.saveRestrictions()
        dispatcher.scheduler.runCurrent()

        val oldRequestId = requireNotNull(pendingStore.peek()).requestId
        pendingStore.requestDietarySetup("person@example.com")
        dietaryRepository.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertTrue(pendingStore.peekForAccount("person@example.com")!!.requestId != oldRequestId)
    }

    @Test
    fun accountChangeDuringProfileResolutionPreventsCatalogFetchAndSave() {
        existingResolver.gate = CompletableDeferred()
        existingResolver.ignoreCancellation = true
        viewModel.beginPendingSetup()
        dispatcher.scheduler.runCurrent()

        sessionStore.saveSession(sessionFor(22L, "other@example.com"))
        pendingStore.requestDietarySetup("other@example.com")
        dispatcher.scheduler.runCurrent()
        existingResolver.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, dietaryRepository.catalogCalls)
        assertEquals(0, dietaryRepository.saveCalls)
        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertEquals("other@example.com", pendingStore.peek()?.accountEmail)
    }

    private fun newViewModel() = AuthenticatedDietaryOnboardingViewModel(
        authSessionStore = sessionStore,
        pendingOnboardingStore = pendingStore,
        dietaryRestrictionRepository = dietaryRepository,
        existingSelfProfileResolver = existingResolver,
        activeProfileManager = activeProfileManager,
    )

    private class FakeDietaryRepository : DietaryRestrictionRepository {
        var catalogCalls = 0
        var saveCalls = 0
        var saveResult = true
        var lastSavedProfileId: Long? = null
        var lastSavedSelections: Map<Long, String>? = null
        var gate: CompletableDeferred<Unit>? = null
        var ignoreCancellation = false

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
            if (ignoreCancellation) {
                withContext(NonCancellable) { gate?.await() }
            } else {
                gate?.await()
            }
            return saveResult
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
