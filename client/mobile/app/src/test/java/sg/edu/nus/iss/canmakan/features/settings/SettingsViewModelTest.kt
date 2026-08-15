package sg.edu.nus.iss.canmakan.features.settings

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
        viewModel = SettingsViewModel(repository)
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

    private class FakeAuthRepository : AuthRepository {
        var result: AuthResult<Unit> = AuthResult.Success(Unit)
        var deleteCalls = 0

        override suspend fun login(
            email: String,
            password: String,
        ): AuthResult<AuthenticatedSession> = error("unused")

        override suspend fun getCurrentUser(): AuthResult<AuthenticatedUser> = error("unused")

        override suspend fun deleteOwnAccount(): AuthResult<Unit> {
            deleteCalls++
            return result
        }
    }
}
