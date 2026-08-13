package sg.edu.nus.iss.canmakan.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationFailureType
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRepository
import sg.edu.nus.iss.canmakan.features.auth.data.AuthResult
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationRepository
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationResponse
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationResult
import sg.edu.nus.iss.canmakan.features.auth.onboarding.PendingOnboardingStore
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationStore

enum class RegistrationStep {
    ACCOUNT_INFORMATION,
    COMPLETE,
}

data class RegistrationUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val step: RegistrationStep = RegistrationStep.ACCOUNT_INFORMATION,
    val isSubmitting: Boolean = false,
    val registrationError: String? = null,
    val registrationFailureType: RegistrationFailureType? = null,
    val account: RegistrationResponse? = null,
    val authenticatedUser: AuthenticatedUser? = null,
    val accountCreatedButLoginFailed: Boolean = false,
) {
    override fun toString(): String {
        return "RegistrationUiState(name=$name, email=$email, password=<redacted>, " +
            "confirmPassword=<redacted>, step=$step, isSubmitting=$isSubmitting, " +
            "accountCreated=${account != null}, authenticated=${authenticatedUser != null})"
    }
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val registrationRepository: RegistrationRepository,
    private val authRepository: AuthRepository,
    private val authSessionStore: AuthSessionStore,
    private val pendingOnboardingStore: PendingOnboardingStore,
    private val pendingInvitationStore: PendingInvitationStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    fun setInvitationToken(token: String?) {
        val value = token?.trim()?.takeIf { it.isNotEmpty() }
        if (value != null) {
            // Keep for post-login continuation claim; registration never claims or consumes it.
            pendingInvitationStore.offer(value)
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = null,
            registrationError = null,
            registrationFailureType = null,
        )
    }

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email,
            emailError = null,
            registrationError = null,
            registrationFailureType = null,
        )
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordError = null,
            confirmPasswordError = null,
            registrationError = null,
            registrationFailureType = null,
        )
    }

    fun updateConfirmPassword(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(
            confirmPassword = confirmPassword,
            confirmPasswordError = null,
            registrationError = null,
            registrationFailureType = null,
        )
    }

    fun createAccount() {
        val state = _uiState.value
        if (state.isSubmitting || state.account != null) return
        val normalizedName = state.name.trim()
        val normalizedEmail = state.email.trim()
        val nameError = when {
            normalizedName.isEmpty() -> "Profile Name is required."
            normalizedName.length > MAX_NAME_LENGTH -> "Profile Name must not exceed 100 characters."
            else -> null
        }
        val emailError = when {
            normalizedEmail.isEmpty() -> "Email is required."
            normalizedEmail.length > MAX_EMAIL_LENGTH -> "Email must not exceed 255 characters."
            !EMAIL_PATTERN.matches(normalizedEmail) -> "Enter a valid email address."
            else -> null
        }
        val passwordError = when {
            state.password.isBlank() -> "Password is required."
            state.password.length < MIN_PASSWORD_LENGTH ->
                "Password must be at least 8 characters."
            utf8ByteLength(state.password) > MAX_PASSWORD_UTF8_BYTES ->
                "Password must not exceed 72 UTF-8 bytes."
            !meetsRegistrationPasswordPolicy(state.password) -> PASSWORD_STRENGTH_MESSAGE
            else -> null
        }
        val confirmPasswordError = when {
            state.confirmPassword.isEmpty() -> "Confirm your password."
            state.confirmPassword != state.password -> "Passwords do not match."
            else -> null
        }

        if (nameError != null || emailError != null || passwordError != null || confirmPasswordError != null) {
            _uiState.value = state.copy(
                nameError = nameError,
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError,
                registrationError = null,
                registrationFailureType = null,
            )
            return
        }

        _uiState.value = state.copy(
            nameError = nameError,
            emailError = emailError,
            passwordError = passwordError,
            confirmPasswordError = confirmPasswordError,
            registrationError = null,
            registrationFailureType = null,
            isSubmitting = true,
        )

        viewModelScope.launch {
            when (val result = registrationRepository.register(
                email = state.email.trim().lowercase(Locale.ROOT),
                password = state.password,
            )) {
                is RegistrationResult.Success -> handleAccountCreated(result.account, state)
                is RegistrationResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        step = RegistrationStep.ACCOUNT_INFORMATION,
                        isSubmitting = false,
                        registrationError = result.message,
                        registrationFailureType = result.type,
                    )
                }
            }
        }
    }

    private suspend fun handleAccountCreated(
        account: RegistrationResponse,
        submittedState: RegistrationUiState,
    ) {
        pendingOnboardingStore.requestDietarySetup(
            accountEmail = account.email,
            accountName = submittedState.name,
        )
        val loginResult = try {
            authRepository.login(account.email, submittedState.password)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
        if (loginResult is AuthResult.Success && authSessionStore.saveSession(loginResult.value)) {
            _uiState.value = _uiState.value.copy(
                step = RegistrationStep.COMPLETE,
                isSubmitting = false,
                password = "",
                confirmPassword = "",
                account = account,
                authenticatedUser = loginResult.value.user,
                accountCreatedButLoginFailed = false,
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            step = RegistrationStep.COMPLETE,
            isSubmitting = false,
            password = "",
            confirmPassword = "",
            account = account,
            authenticatedUser = null,
            accountCreatedButLoginFailed = true,
            registrationError = AUTO_LOGIN_FAILURE_MESSAGE,
        )
    }

    companion object {
        private const val MAX_NAME_LENGTH = 100
        private const val MAX_EMAIL_LENGTH = 255
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_UTF8_BYTES = 72
        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        private const val PASSWORD_STRENGTH_MESSAGE =
            "Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character."
        const val AUTO_LOGIN_FAILURE_MESSAGE =
            "Your account was created, but automatic sign-in failed. Log in to continue."

        private fun utf8ByteLength(value: String): Int =
            value.toByteArray(Charsets.UTF_8).size

        private fun meetsRegistrationPasswordPolicy(password: String): Boolean {
            if (password.length < MIN_PASSWORD_LENGTH) return false
            var hasUpper = false
            var hasLower = false
            var hasDigit = false
            var hasSpecial = false
            for (character in password) {
                when {
                    character.isUpperCase() -> hasUpper = true
                    character.isLowerCase() -> hasLower = true
                    character.isDigit() -> hasDigit = true
                    else -> hasSpecial = true
                }
            }
            return hasUpper && hasLower && hasDigit && hasSpecial
        }
    }
}
