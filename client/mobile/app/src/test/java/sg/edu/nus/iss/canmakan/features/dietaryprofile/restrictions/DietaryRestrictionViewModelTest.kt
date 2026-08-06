package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager

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
    private lateinit var repository: FakeDietaryRestrictionRepository
    private lateinit var viewModel: DietaryRestrictionViewModel

    // Set up the test environment
    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        activeProfileManager = ActiveProfileManager()
        repository = FakeDietaryRestrictionRepository()
        viewModel = DietaryRestrictionViewModel(activeProfileManager, repository)
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
        activeProfileManager.switchProfile(999L)
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
        viewModel.toggleDietaryRestriction(20L)
        viewModel.toggleDietaryRestriction(21L)

        var saved = false
        viewModel.onSave { saved = true }

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(saved)
        assertEquals(mapOf(20L to "STRICT_AVOID", 21L to "STRICT_AVOID"), repository.lastSavedSelections)
    }

    // Fake repository for testing
    // Avoid using real repo in tests
    private class FakeDietaryRestrictionRepository : DietaryRestrictionRepository {
        var savedSelections: Map<Long, String> = emptyMap()
        var lastSavedSelections: Map<Long, String> = emptyMap()

        override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> {
            return listOf(
                DietaryRestriction(10L, "HALAL", "Halal", "RELIGIOUS"),
                DietaryRestriction(11L, "VEGETARIAN", "Vegetarian", "RELIGIOUS"),
                DietaryRestriction(20L, "PEANUT", "Peanut Allergy", "ALLERGEN"),
                DietaryRestriction(21L, "MILK", "Milk Allergy", "ALLERGEN"),
                DietaryRestriction(30L, "LOW_CARB", "Low Carb", "DIET")
            )
        }

        override suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String> {
            return savedSelections
        }

        override suspend fun saveDietaryRestrictionSelections(profileId: Long, selections: Map<Long, String>): Boolean {
            lastSavedSelections = selections
            return true
        }
    }
}
