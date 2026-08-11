package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.testing.signInTestUser
import sg.edu.nus.iss.canmakan.testing.testAuthSessionStore

/*
    Mobile Test Cases for Use Case 1: Update App User Dietary Profile

    @author Amelia
 */
class DietaryRestrictionViewModelTest {

    // Test dispatcher: allows for asynchronous testing
    // Active profile mgr: allows for switching between profiles
    // Repository: allows for loading and saving dietary restrictions
    // View model: the object under test
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var activeProfileManager: ActiveProfileManager
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var repository: FakeDietaryRestrictionRepository
    private lateinit var viewModel: DietaryRestrictionViewModel

    // Set up the test environment
    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        activeProfileManager = ActiveProfileManager()
        sessionStore = testAuthSessionStore().also { it.signInTestUser() }
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 1L)
        repository = FakeDietaryRestrictionRepository()
        viewModel = DietaryRestrictionViewModel(activeProfileManager, repository, sessionStore)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    // Clean up the test environment
    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Testing the loading of dietary restrictions
    @Test
    @DisplayName("UC1 M1: Loads dietary restrictions for the active profile")
    fun loadsDietaryRestrictionsForActiveProfile() = runTest {
        repository.savedSelections = mapOf(1L to "STRICT_AVOID")

        // Trigger profile reload
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 999L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(2, uiState.religiousRestrictions.size)
        assertEquals(2, uiState.allergenRestrictions.size)
        assertEquals(1, uiState.dietRestrictions.size)
        assertTrue(uiState.selectedRestrictions.containsKey(1L))
    }

    // Testing the selection of dietary restrictions
    @Test
    @DisplayName("UC1 M2: Allows only one religious restriction to be selected")
    fun allowsOnlyOneReligiousRestrictionSelection() = runTest {
        // Catalog must be loaded so religious IDs are known for mutual exclusion.
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.religiousRestrictions.size)

        viewModel.selectReligiousRestriction(10L)
        viewModel.selectReligiousRestriction(11L)

        val selected = viewModel.uiState.value.selectedRestrictions
        assertEquals(1, selected.size)
        assertTrue(selected.containsKey(11L))
        assertEquals("STRICT_AVOID", selected[11L])
    }

    // Testing the toggling of multiple dietary restrictions
    @Test
    @DisplayName("UC1 M3: Toggles multiple dietary restrictions for non-religious categories")
    fun togglesMultipleDietaryRestrictions() {
        viewModel.toggleDietaryRestriction(20L)
        viewModel.toggleDietaryRestriction(21L)

        val selected = viewModel.uiState.value.selectedRestrictions
        assertEquals(2, selected.size)
        assertTrue(selected.containsKey(20L))
        assertTrue(selected.containsKey(21L))
    }

    // Testing the saving of dietary restrictions
    @Test
    @DisplayName("UC1 M4: Saves current selections and reports success")
    fun savesCurrentSelectionsAndReportsSuccess() = runTest {
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 42L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleDietaryRestriction(20L)
        viewModel.toggleDietaryRestriction(21L)

        var saved = false
        viewModel.onSave { saved = true }

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(saved)
        assertEquals(mapOf(20L to "STRICT_AVOID", 21L to "STRICT_AVOID"), repository.lastSavedSelections)
    }

    // UC1-AC13: loading state while the catalog loads.
    @Test
    @DisplayName("UC1 M5: Shows a loading state while the catalog loads")
    fun showsLoadingStateWhileCatalogLoads() = runTest {
        repository.gate = CompletableDeferred()

        val job = launch { viewModel.loadDietaryRestrictions() }
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        repository.gate?.complete(Unit)
        job.join()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // UC1-AC14: empty state when the catalog is empty, without crashing.
    @Test
    @DisplayName("UC1 M6: Shows an empty state when the catalog is empty")
    fun showsEmptyStateWhenCatalogIsEmpty() = runTest {
        repository.catalog = emptyList()

        viewModel.loadDietaryRestrictions()

        val uiState = viewModel.uiState.value
        assertTrue(uiState.religiousRestrictions.isEmpty())
        assertTrue(uiState.allergenRestrictions.isEmpty())
        assertTrue(uiState.dietRestrictions.isEmpty())
        assertFalse(uiState.isLoading)
        assertNull(uiState.errorMessage)
    }

    // UC1-AC15: error state on a catalog load failure, without crashing. Uses a
    // fresh repository/ViewModel (rather than the shared one from setUp, which
    // has already completed one successful load) so this is a genuine first
    // load failing, not a reload over already-loaded data.
    @Test
    @DisplayName("UC1 M7: Shows an error state when the catalog fails to load")
    fun showsErrorStateWhenCatalogLoadFails() = runTest {
        val failingRepository = FakeDietaryRestrictionRepository().apply { loadShouldThrow = true }
        val freshManager = ActiveProfileManager().also {
            it.switchProfile(
                requireNotNull(sessionStore.accountKey.value),
                1L,
            )
        }
        val freshViewModel = DietaryRestrictionViewModel(
            freshManager,
            failingRepository,
            testAuthSessionStore().also { it.signInTestUser() },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = freshViewModel.uiState.value
        assertNotNull(uiState.errorMessage)
        assertFalse(uiState.isLoading)
        assertTrue(uiState.religiousRestrictions.isEmpty())
    }

    // UC1-AC15: error state on a save failure, without crashing and without
    // reporting false success.
    @Test
    @DisplayName("UC1 M8: Shows an error state when saving fails")
    fun showsErrorStateWhenSaveFails() = runTest {
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 42L)
        testDispatcher.scheduler.advanceUntilIdle()
        repository.saveShouldSucceed = false
        viewModel.toggleDietaryRestriction(20L)

        var succeeded = false
        viewModel.onSave { succeeded = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(succeeded)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun profilelessSaveIsBlockedBeforeRepositoryCall() = runTest {
        activeProfileManager.reset()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleDietaryRestriction(20L)

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.saveCalls)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("profile setup") == true)
    }

    @Test
    fun accountSwitchClearsSelectionsImmediatelyAndIgnoresStaleLoad() = runTest {
        repository.savedSelectionsByProfile[42L] = mapOf(10L to "STRICT_AVOID")
        repository.blockedLoadProfileId = 42L
        repository.loadGate = CompletableDeferred()
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 42L)
        testDispatcher.scheduler.runCurrent()

        repository.savedSelectionsByProfile[84L] = mapOf(21L to "STRICT_AVOID")
        sessionStore.signInTestUser(22L, "other@example.com")
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 84L)
        testDispatcher.scheduler.runCurrent()

        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)
        assertFalse(viewModel.uiState.value.selectedRestrictions.containsKey(10L))

        repository.loadGate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)
    }

    @Test
    fun staleSaveCannotCallSuccessOrMutateNewAccountsState() = runTest {
        viewModel.toggleDietaryRestriction(20L)
        repository.blockedSaveProfileId = 1L
        repository.saveGate = CompletableDeferred()
        var successCalled = false
        viewModel.onSave { successCalled = true }
        testDispatcher.scheduler.runCurrent()

        repository.savedSelectionsByProfile[84L] = mapOf(21L to "STRICT_AVOID")
        sessionStore.signInTestUser(22L, "other@example.com")
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 84L)
        testDispatcher.scheduler.runCurrent()

        repository.saveGate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(successCalled)
        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun oldSessionResultIsIgnoredAfterAccountCyclesBackToSameUserId() = runTest {
        repository.savedSelectionsByProfile[42L] = mapOf(10L to "STRICT_AVOID")
        repository.blockedLoadProfileId = 42L
        repository.loadGate = CompletableDeferred()
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 42L)
        testDispatcher.scheduler.runCurrent()

        sessionStore.signInTestUser(22L, "other@example.com")
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 84L)
        testDispatcher.scheduler.runCurrent()

        repository.savedSelectionsByProfile[42L] = mapOf(21L to "STRICT_AVOID")
        sessionStore.signInTestUser(TEST_USER_ID, "person@example.com")
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 42L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)

        repository.loadGate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)
    }

    // Fake repository for testing
    // Avoid using real repo in tests
    private class FakeDietaryRestrictionRepository : DietaryRestrictionRepository {
        var savedSelections: Map<Long, String> = emptyMap()
        val savedSelectionsByProfile = mutableMapOf<Long, Map<Long, String>>()
        var lastSavedSelections: Map<Long, String> = emptyMap()
        var catalog: List<DietaryRestriction> = listOf(
            DietaryRestriction(10L, "HALAL", "Halal", "RELIGIOUS"),
            DietaryRestriction(11L, "VEGETARIAN", "Vegetarian", "RELIGIOUS"),
            DietaryRestriction(20L, "PEANUT", "Peanut Allergy", "ALLERGEN"),
            DietaryRestriction(21L, "MILK", "Milk Allergy", "ALLERGEN"),
            DietaryRestriction(30L, "LOW_CARB", "Low Carb", "DIET")
        )
        var gate: CompletableDeferred<Unit>? = null
        var loadShouldThrow = false
        var saveShouldSucceed = true
        var saveCalls = 0
        var blockedLoadProfileId: Long? = null
        var loadGate: CompletableDeferred<Unit>? = null
        var blockedSaveProfileId: Long? = null
        var saveGate: CompletableDeferred<Unit>? = null

        override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> {
            gate?.await()
            if (loadShouldThrow) throw java.io.IOException("network down")
            return catalog
        }

        override suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String> {
            val result = savedSelectionsByProfile[profileId] ?: savedSelections
            if (profileId == blockedLoadProfileId) {
                blockedLoadProfileId = null
                withContext(NonCancellable) { loadGate?.await() }
            }
            return result
        }

        override suspend fun saveDietaryRestrictionSelections(profileId: Long, selections: Map<Long, String>): Boolean {
            saveCalls++
            lastSavedSelections = selections
            if (profileId == blockedSaveProfileId) {
                withContext(NonCancellable) { saveGate?.await() }
            }
            return saveShouldSucceed
        }
    }

    private companion object {
        const val TEST_USER_ID = 14L
    }
}
