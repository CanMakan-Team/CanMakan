package sg.edu.nus.iss.canmakan.features.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationFailureType
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationRepository
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationResponse
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationResult
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC18: Android registration ViewModel")
class RegistrationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var registrationRepository: FakeRegistrationRepository
    private lateinit var dietaryRepository: FakeDietaryRestrictionRepository
    private lateinit var viewModel: RegistrationViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        registrationRepository = FakeRegistrationRepository()
        dietaryRepository = FakeDietaryRestrictionRepository()
        viewModel = RegistrationViewModel(registrationRepository, dietaryRepository)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("UC18 M1: valid account input advances to optional dietary setup without API call")
    fun validInputAdvancesWithoutRegistering() {
        enterValidAccountInformation()

        assertEquals(RegistrationStep.OPTIONAL_DIETARY_PROFILE, viewModel.uiState.value.step)
        assertEquals(0, registrationRepository.callCount)
    }

    @Test
    @DisplayName("UC18 M2: invalid email remains on account information")
    fun invalidEmailStaysOnAccountStep() {
        viewModel.updateName("Person Name")
        viewModel.updateEmail("not-an-email")
        viewModel.updatePassword("Password1!")
        viewModel.updateConfirmPassword("Password1!")
        viewModel.continueToDietaryProfile()

        assertEquals(RegistrationStep.ACCOUNT_INFORMATION, viewModel.uiState.value.step)
        assertEquals("Enter a valid email address.", viewModel.uiState.value.emailError)
    }

    @Test
    @DisplayName("UC18 M2b: blank name remains on account information")
    fun blankNameStaysOnAccountStep() {
        viewModel.updateEmail("person@example.com")
        viewModel.updatePassword("Password1!")
        viewModel.updateConfirmPassword("Password1!")
        viewModel.continueToDietaryProfile()

        assertEquals(RegistrationStep.ACCOUNT_INFORMATION, viewModel.uiState.value.step)
        assertEquals("Name is required.", viewModel.uiState.value.nameError)
    }

    @Test
    @DisplayName("UC18 M2c: name shorter than three characters remains on account information")
    fun shortNameStaysOnAccountStep() {
        viewModel.updateName("Al")
        viewModel.updateEmail("person@example.com")
        viewModel.updatePassword("Password1!")
        viewModel.updateConfirmPassword("Password1!")
        viewModel.continueToDietaryProfile()

        assertEquals(RegistrationStep.ACCOUNT_INFORMATION, viewModel.uiState.value.step)
        assertEquals("Name must be at least 3 characters.", viewModel.uiState.value.nameError)
    }

    @Test
    @DisplayName("UC18 M2d: name longer than 100 characters remains on account information")
    fun longNameStaysOnAccountStep() {
        viewModel.updateName("A".repeat(101))
        viewModel.updateEmail("person@example.com")
        viewModel.updatePassword("Password1!")
        viewModel.updateConfirmPassword("Password1!")
        viewModel.continueToDietaryProfile()

        assertEquals(RegistrationStep.ACCOUNT_INFORMATION, viewModel.uiState.value.step)
        assertEquals(
            "Name must be between 3 and 100 characters.",
            viewModel.uiState.value.nameError,
        )
        assertEquals(0, registrationRepository.callCount)
    }

    @Test
    @DisplayName("UC18 M2e: password over 72 UTF-8 bytes remains on account information")
    fun passwordOverBcryptLimitStaysOnAccountStep() {
        val tooLongPassword = "é".repeat(37) // 37 * 2 bytes = 74 UTF-8 bytes
        viewModel.updateName("Person Name")
        viewModel.updateEmail("person@example.com")
        viewModel.updatePassword(tooLongPassword)
        viewModel.updateConfirmPassword(tooLongPassword)
        viewModel.continueToDietaryProfile()

        assertEquals(RegistrationStep.ACCOUNT_INFORMATION, viewModel.uiState.value.step)
        assertEquals(
            "Password must not exceed 72 UTF-8 bytes.",
            viewModel.uiState.value.passwordError,
        )
        assertEquals(0, registrationRepository.callCount)
    }

    @Test
    @DisplayName("UC18 M2f: weak password without special character remains on account information")
    fun weakPasswordStaysOnAccountStep() {
        viewModel.updateName("Person Name")
        viewModel.updateEmail("person@example.com")
        viewModel.updatePassword("Password1")
        viewModel.updateConfirmPassword("Password1")
        viewModel.continueToDietaryProfile()

        assertEquals(RegistrationStep.ACCOUNT_INFORMATION, viewModel.uiState.value.step)
        assertEquals(
            "Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character.",
            viewModel.uiState.value.passwordError,
        )
        assertEquals(0, registrationRepository.callCount)
    }

    @Test
    @DisplayName("UC18 M3: password mismatch remains on account information")
    fun mismatchedPasswordStaysOnAccountStep() {
        viewModel.updateEmail("person@example.com")
        viewModel.updatePassword("Password1!")
        viewModel.updateConfirmPassword("Different1!")
        viewModel.continueToDietaryProfile()

        assertEquals(RegistrationStep.ACCOUNT_INFORMATION, viewModel.uiState.value.step)
        assertEquals("Passwords do not match.", viewModel.uiState.value.confirmPasswordError)
    }

    @Test
    @DisplayName("UC18 M4: account creation exposes loading state")
    fun accountCreationExposesLoadingState() {
        enterValidAccountInformation()
        registrationRepository.gate = CompletableDeferred()

        viewModel.createAccount()
        testDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isSubmitting)
        registrationRepository.gate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    @DisplayName("UC18 M5: 201 without dietary data completes registration without profile save")
    fun successWithoutDietaryDataCompletesWithoutProfileCall() {
        enterValidAccountInformation()

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RegistrationStep.COMPLETE, state.step)
        assertEquals(14L, state.account?.userId)
        assertEquals(ProfileSetupStatus.NOT_REQUESTED, state.profileSetupStatus)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertEquals(0, dietaryRepository.saveCalls)
    }

    @Test
    @DisplayName("UC18 M6: request normalizes email and name but preserves password exactly")
    fun requestNormalizesEmailAndPreservesPassword() {
        val rawPassword = "  KeepCase Password1!  "
        viewModel.updateName("  Person Name  ")
        viewModel.updateEmail("  Person@Example.COM  ")
        viewModel.updatePassword(rawPassword)
        viewModel.updateConfirmPassword(rawPassword)
        viewModel.continueToDietaryProfile()

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Person Name", registrationRepository.lastName)
        assertEquals("person@example.com", registrationRepository.lastEmail)
        assertEquals(rawPassword, registrationRepository.lastPassword)
    }

    @Test
    @DisplayName("UC18 M7: duplicate email returns to account step and preserves every field")
    fun duplicateEmailPreservesInput() {
        registrationRepository.result = RegistrationResult.Failure(
            RegistrationFailureType.DUPLICATE_EMAIL,
            "An account with this email already exists.",
        )
        enterValidAccountInformation()
        val before = viewModel.uiState.value

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RegistrationStep.ACCOUNT_INFORMATION, state.step)
        assertEquals(before.email, state.email)
        assertEquals(before.password, state.password)
        assertEquals(before.confirmPassword, state.confirmPassword)
        assertEquals("An account with this email already exists.", state.registrationError)
    }

    @Test
    @DisplayName("UC18 M8: backend 400 returns to account step with validation message")
    fun backendValidationFailureIsShown() {
        registrationRepository.result = RegistrationResult.Failure(
            RegistrationFailureType.INVALID_REQUEST,
            "Invalid registration request.",
        )
        enterValidAccountInformation()

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RegistrationStep.ACCOUNT_INFORMATION, viewModel.uiState.value.step)
        assertEquals("Invalid registration request.", viewModel.uiState.value.registrationError)
    }

    @Test
    @DisplayName("UC18 M9: backend 500 returns a generic error without losing input")
    fun backendFailureIsSafeAndPreservesInput() {
        registrationRepository.result = RegistrationResult.Failure(
            RegistrationFailureType.SERVER,
            "Registration could not be completed.",
        )
        enterValidAccountInformation()

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Registration could not be completed.", state.registrationError)
        assertEquals("  Person@Example.COM  ", state.email)
        assertEquals("Password1!", state.password)
    }

    @Test
    @DisplayName("UC18 M10: account failure never attempts profile persistence")
    fun accountFailureNeverAttemptsProfilePersistence() {
        registrationRepository.result = RegistrationResult.Failure(
            RegistrationFailureType.SERVER,
            "Registration could not be completed.",
        )
        enterValidAccountInformation()
        viewModel.toggleRestriction(20L)

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.account)
        assertEquals(0, dietaryRepository.saveCalls)
    }

    @Test
    @DisplayName("UC18 M11: selected restrictions are actually saved and the flow completes")
    fun selectedDietaryDataIsSavedAndCompletesAfterAccountSuccess() {
        enterValidAccountInformation()
        viewModel.toggleRestriction(20L)

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.account)
        assertEquals(RegistrationStep.COMPLETE, state.step)
        assertEquals(ProfileSetupStatus.SELECTED, state.profileSetupStatus)
        assertEquals(1, dietaryRepository.saveCalls)
        assertEquals(77L, dietaryRepository.lastProfileId)
        assertEquals(setOf(20L), dietaryRepository.lastSelections?.keys)
    }

    @Test
    @DisplayName("UC18 M11b: a genuine save failure defers profile setup with an accurate message")
    fun saveFailureDefersProfileSetup() {
        dietaryRepository.saveShouldSucceed = false
        enterValidAccountInformation()
        viewModel.toggleRestriction(20L)

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.account)
        assertEquals(RegistrationStep.OPTIONAL_DIETARY_PROFILE, state.step)
        assertEquals(ProfileSetupStatus.DEFERRED_UNAVAILABLE, state.profileSetupStatus)
        assertEquals(RegistrationViewModel.PROFILE_SETUP_DEFERRED_MESSAGE, state.profileSetupMessage)
        assertEquals(1, dietaryRepository.saveCalls)
    }

    @Test
    @DisplayName("UC18 M11c: a save exception is treated the same as a failed save, not a crash")
    fun saveExceptionDefersProfileSetupInsteadOfCrashing() {
        dietaryRepository.saveShouldThrow = true
        enterValidAccountInformation()
        viewModel.toggleRestriction(20L)

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileSetupStatus.DEFERRED_UNAVAILABLE, viewModel.uiState.value.profileSetupStatus)
    }

    @Test
    @DisplayName("UC18 M12: completing profile later never re-registers the created account")
    fun completingLaterDoesNotRetryRegistration() {
        dietaryRepository.saveShouldSucceed = false
        enterValidAccountInformation()
        viewModel.toggleRestriction(20L)
        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.createAccount()
        viewModel.completeProfileSetupLater()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, registrationRepository.callCount)
        assertEquals(RegistrationStep.COMPLETE, viewModel.uiState.value.step)
        assertNotNull(viewModel.uiState.value.account)
    }

    @Test
    @DisplayName("UC18 M13: registration state string never exposes passwords")
    fun registrationStateStringRedactsPasswords() {
        viewModel.updatePassword("Password1!")
        viewModel.updateConfirmPassword("Password1!")

        val rendered = viewModel.uiState.value.toString()

        assertFalse(rendered.contains("Password1!"))
        assertTrue(rendered.contains("password=<redacted>"))
    }

    @Test
    @DisplayName("UC18 M14: only one religious restriction remains selected")
    fun religiousSelectionsAreMutuallyExclusive() {
        enterValidAccountInformation()

        viewModel.toggleRestriction(10L)
        viewModel.toggleRestriction(11L)

        assertEquals(setOf(11L), viewModel.uiState.value.selectedRestrictionIds)
    }

    private fun enterValidAccountInformation() {
        viewModel.updateName("Person Name")
        viewModel.updateEmail("  Person@Example.COM  ")
        viewModel.updatePassword("Password1!")
        viewModel.updateConfirmPassword("Password1!")
        viewModel.continueToDietaryProfile()
    }

    private class FakeRegistrationRepository : RegistrationRepository {
        var result: RegistrationResult = RegistrationResult.Success(
            RegistrationResponse(14L, 77L, "Person Name", "person@example.com", true)
        )
        var gate: CompletableDeferred<Unit>? = null
        var callCount = 0
        var lastName: String? = null
        var lastEmail: String? = null
        var lastPassword: String? = null

        override suspend fun register(name: String, email: String, password: String): RegistrationResult {
            callCount++
            lastName = name
            lastEmail = email
            lastPassword = password
            gate?.await()
            return result
        }
    }

    private class FakeDietaryRestrictionRepository : DietaryRestrictionRepository {
        var saveCalls = 0
        var saveShouldSucceed = true
        var saveShouldThrow = false
        var lastProfileId: Long? = null
        var lastSelections: Map<Long, String>? = null

        override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> {
            return listOf(
                DietaryRestriction(10L, "HALAL", "Halal", "RELIGIOUS"),
                DietaryRestriction(11L, "VEGETARIAN", "Vegetarian", "RELIGIOUS"),
                DietaryRestriction(20L, "PEANUT", "Peanut allergy", "ALLERGEN"),
            )
        }

        override suspend fun getDietaryRestrictionsForProfile(
            profileId: Long,
        ): Map<Long, String> = emptyMap()

        override suspend fun saveDietaryRestrictionSelections(
            profileId: Long,
            selections: Map<Long, String>,
        ): Boolean {
            saveCalls++
            lastProfileId = profileId
            lastSelections = selections
            if (saveShouldThrow) throw java.io.IOException("network error")
            return saveShouldSucceed
        }
    }
}
