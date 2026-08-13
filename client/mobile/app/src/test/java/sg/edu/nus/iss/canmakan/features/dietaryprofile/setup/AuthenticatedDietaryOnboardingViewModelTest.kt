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
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.ProfileRestrictionSeverity
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.SelfProfileRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.SelfProfileResponse
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.SelfProfileSetupResult
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatedDietaryOnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var pendingStore: PendingOnboardingStore
    private lateinit var dietaryRepository: FakeDietaryRepository
    private lateinit var selfProfileRepository: FakeSelfProfileRepository
    private lateinit var activeProfileManager: ActiveProfileManager
    private lateinit var viewModel: AuthenticatedDietaryOnboardingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessionStore = AuthSessionStore(FakeSessionPersistence(), Gson())
        sessionStore.saveSession(validSession())
        pendingStore = PendingOnboardingStore().also {
            it.requestDietarySetup("person@example.com", "Person Name")
        }
        dietaryRepository = FakeDietaryRepository()
        selfProfileRepository = FakeSelfProfileRepository()
        activeProfileManager = ActiveProfileManager()
        viewModel = newViewModel()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsSharedCatalogAndPendingProfileNameAfterAuthenticatedSetupBegins() {
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, dietaryRepository.catalogCalls)
        assertEquals("Person Name", viewModel.uiState.value.profileName)
        assertFalse(viewModel.uiState.value.profileNameEditable)
        assertEquals(3, viewModel.uiState.value.restrictions.size)
    }

    @Test
    fun successfulSaveCreatesAuthenticatedSelfProfileAndKeepsSession() {
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleRestriction(2L)
        viewModel.setSeverity(2L, ProfileRestrictionSeverity.INTOLERANCE)

        viewModel.saveRestrictions()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, selfProfileRepository.createCalls)
        assertEquals("Person Name", selfProfileRepository.lastProfileName)
        assertEquals(mapOf(2L to ProfileRestrictionSeverity.INTOLERANCE), selfProfileRepository.lastSelections)
        assertEquals(88L, activeProfileManager.currentProfileId.value)
        assertNull(pendingStore.peek())
        assertEquals(TEST_ACCESS_TOKEN, sessionStore.currentAccessToken())
        assertTrue(viewModel.uiState.value.resolved)
    }

    @Test
    fun creationFailureLeavesAccountSessionAndPendingSetupForRetry() {
        selfProfileRepository.result = SelfProfileSetupResult.Failure("Profile setup unavailable.")
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleRestriction(2L)

        viewModel.saveRestrictions()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Profile setup unavailable.", viewModel.uiState.value.errorMessage)
        assertEquals("person@example.com", pendingStore.peek()?.accountEmail)
        assertEquals(TEST_ACCESS_TOKEN, sessionStore.currentAccessToken())
        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertFalse(viewModel.uiState.value.resolved)
    }

    @Test
    fun emptySelectionDoesNotCreateAnEmptyProfile() {
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveRestrictions()

        assertEquals(0, selfProfileRepository.createCalls)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("Select at least one") == true)
    }

    @Test
    fun explicitDeferCreatesNoProfileAndLeavesSessionUntouched() {
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.deferSetup()

        assertEquals(0, selfProfileRepository.createCalls)
        assertNull(pendingStore.peek())
        assertEquals(TEST_ACCESS_TOKEN, sessionStore.currentAccessToken())
        assertTrue(viewModel.uiState.value.resolved)
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
    fun accountChangeWhileCreationIsInFlightCannotClearOrSwitchProfile() {
        selfProfileRepository.gate = CompletableDeferred()
        selfProfileRepository.ignoreCancellation = true
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleRestriction(2L)
        viewModel.saveRestrictions()
        dispatcher.scheduler.runCurrent()

        sessionStore.saveSession(sessionFor(22L, "other@example.com"))
        pendingStore.requestDietarySetup("other@example.com", "Other Person")
        dispatcher.scheduler.runCurrent()
        selfProfileRepository.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, activeProfileManager.currentProfileId.value)
        assertEquals("other@example.com", pendingStore.peekForAccount("other@example.com")?.accountEmail)
    }

    @Test
    fun existingAccountSetupCanEnterAProfileNameWhenNoPendingNameExists() {
        pendingStore.requestDietarySetup("person@example.com")
        viewModel = newViewModel()
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.profileNameEditable)
        viewModel.updateProfileName("Later Profile")
        viewModel.toggleRestriction(2L)
        viewModel.saveRestrictions()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Later Profile", selfProfileRepository.lastProfileName)
    }

    @Test
    fun laterSetupRejectsAProfileNameBeyondTheSharedLimit() {
        pendingStore.requestDietarySetup("person@example.com")
        viewModel = newViewModel()
        viewModel.beginPendingSetup()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateProfileName("A".repeat(101))
        viewModel.toggleRestriction(2L)
        viewModel.saveRestrictions()

        assertEquals(0, selfProfileRepository.createCalls)
        assertEquals(
            "Profile name must not exceed 100 characters.",
            viewModel.uiState.value.errorMessage,
        )
    }

    private fun newViewModel() = AuthenticatedDietaryOnboardingViewModel(
        authSessionStore = sessionStore,
        pendingOnboardingStore = pendingStore,
        dietaryRestrictionRepository = dietaryRepository,
        selfProfileRepository = selfProfileRepository,
        activeProfileManager = activeProfileManager,
    )

    private class FakeDietaryRepository : DietaryRestrictionRepository {
        var catalogCalls = 0
        override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> {
            catalogCalls++
            return listOf(
                DietaryRestriction(1L, "HALAL", "Halal", "RELIGIOUS"),
                DietaryRestriction(2L, "PEANUT", "Peanut", "ALLERGEN"),
                DietaryRestriction(3L, "VEGAN", "Vegan", "DIET"),
            )
        }
        override suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String> = emptyMap()
        override suspend fun saveDietaryRestrictionSelections(
            profileId: Long,
            selections: Map<Long, String>,
        ): Boolean = false
    }

    private class FakeSelfProfileRepository : SelfProfileRepository {
        var result: SelfProfileSetupResult = SelfProfileSetupResult.Created(
            SelfProfileResponse(88L, "Person Name", "SELF", true, emptyMap()),
        )
        var createCalls = 0
        var lastProfileName: String? = null
        var lastSelections: Map<Long, ProfileRestrictionSeverity>? = null
        var gate: CompletableDeferred<Unit>? = null
        var ignoreCancellation = false

        override suspend fun createSelfProfile(
            profileName: String,
            restrictions: Map<Long, ProfileRestrictionSeverity>,
        ): SelfProfileSetupResult {
            createCalls++
            lastProfileName = profileName
            lastSelections = restrictions
            if (ignoreCancellation) withContext(NonCancellable) { gate?.await() } else gate?.await()
            return result
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
        const val TEST_ACCESS_TOKEN = "access-token"
        fun validSession() = sessionFor(14L, "person@example.com")
        fun sessionFor(userId: Long, email: String) = AuthenticatedSession(
            accessToken = if (userId == 14L) TEST_ACCESS_TOKEN else "token-$userId",
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(userId, email, AuthRole.USER),
        )
    }
}
