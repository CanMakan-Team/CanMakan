package sg.edu.nus.iss.canmakan.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.auth.data.AuthFailureType
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRepository
import sg.edu.nus.iss.canmakan.features.auth.data.AuthResult
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationStore

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val loginError: String? = null,
    val isSubmitting: Boolean = false,
    val authenticatedUser: AuthenticatedUser? = null,
    val invitationToken: String? = null,
) {
    override fun toString(): String {
        return "LoginUiState(email=$email, password=<redacted>, emailError=$emailError, " +
            "passwordError=$passwordError, loginError=$loginError, " +
            "isSubmitting=$isSubmitting, authenticated=${authenticatedUser != null}, " +
            "hasInvitationToken=${!invitationToken.isNullOrBlank()})"
    }
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authSessionStore: AuthSessionStore,
    private val familyProfileRepository: FamilyProfileRepository,
    private val pendingInvitationStore: PendingInvitationStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun setInvitationToken(token: String?) {
        val trimmed = token?.trim().orEmpty()
        val value = trimmed.ifBlank { null }
        if (value != null) {
            pendingInvitationStore.offer(value)
        }
        _uiState.value = _uiState.value.copy(invitationToken = value)
    }

    fun updateEmail(email: String) {
        val state = _uiState.value
        if (state.isSubmitting || state.authenticatedUser != null) return

        _uiState.value = state.copy(
            email = email,
            emailError = null,
            loginError = null,
        )
    }

    fun updatePassword(password: String) {
        val state = _uiState.value
        if (state.isSubmitting || state.authenticatedUser != null) return

        _uiState.value = state.copy(
            password = password,
            passwordError = null,
            loginError = null,
        )
    }

    fun login() {
        val state = _uiState.value
        if (state.isSubmitting || state.authenticatedUser != null) return

        val trimmedEmail = state.email.trim()
        val emailError = when {
            trimmedEmail.isEmpty() -> EMAIL_REQUIRED_MESSAGE
            !EMAIL_PATTERN.matches(trimmedEmail) -> EMAIL_INVALID_MESSAGE
            else -> null
        }
        val passwordError = if (state.password.isBlank()) {
            PASSWORD_REQUIRED_MESSAGE
        } else {
            null
        }

        if (emailError != null || passwordError != null) {
            _uiState.value = state.copy(
                emailError = emailError,
                passwordError = passwordError,
                loginError = null,
            )
            return
        }

        val normalizedEmail = trimmedEmail.lowercase(Locale.ROOT)
        val exactPassword = state.password

        // Set this before launching so two taps in the same UI frame cannot start two logins.
        _uiState.value = state.copy(
            emailError = null,
            passwordError = null,
            loginError = null,
            isSubmitting = true,
        )

        viewModelScope.launch {
            try {
                when (val result = authRepository.login(normalizedEmail, exactPassword)) {
                    is AuthResult.Success -> {
                        if (authSessionStore.saveSession(result.value)) {
                            claimPendingInvitationIfPresent()
                            _uiState.value = _uiState.value.copy(
                                password = "",
                                loginError = null,
                                authenticatedUser = result.value.user,
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                authenticatedUser = null,
                                loginError = SESSION_ESTABLISHMENT_MESSAGE,
                            )
                        }
                    }
                    is AuthResult.Failure -> showLoginFailure(result.type)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(loginError = INVALID_RESPONSE_MESSAGE)
            } finally {
                _uiState.value = _uiState.value.copy(isSubmitting = false)
            }
        }
    }

    private suspend fun claimPendingInvitationIfPresent() {
        val token = _uiState.value.invitationToken?.trim().orEmpty()
            .ifBlank { pendingInvitationStore.peek().orEmpty() }
        if (token.isBlank()) return
        try {
            familyProfileRepository.claimInvitation(token)
            pendingInvitationStore.clear()
        } catch (_: Exception) {
            // Leave token for authenticated shell retry; login still succeeds.
        }
    }

    private fun showLoginFailure(failureType: AuthFailureType) {
        val message = when (failureType) {
            AuthFailureType.MALFORMED_REQUEST -> MALFORMED_REQUEST_MESSAGE
            AuthFailureType.INVALID_CREDENTIALS,
            AuthFailureType.UNAUTHENTICATED,
            -> INVALID_CREDENTIALS_MESSAGE

            AuthFailureType.FORBIDDEN -> FORBIDDEN_MESSAGE
            AuthFailureType.NETWORK -> NETWORK_MESSAGE
            AuthFailureType.SERVER -> SERVER_MESSAGE
            AuthFailureType.INVALID_RESPONSE -> INVALID_RESPONSE_MESSAGE
        }
        _uiState.value = _uiState.value.copy(loginError = message)
    }

    companion object {
        const val EMAIL_REQUIRED_MESSAGE = "Email is required."
        const val EMAIL_INVALID_MESSAGE = "Enter a valid email address."
        const val PASSWORD_REQUIRED_MESSAGE = "Password is required."
        const val MALFORMED_REQUEST_MESSAGE = "Check your email and password and try again."
        const val INVALID_CREDENTIALS_MESSAGE = "Invalid email or password."
        const val FORBIDDEN_MESSAGE = "This account cannot access CanMakan."
        const val NETWORK_MESSAGE = "Check your connection and try again."
        const val SERVER_MESSAGE = "CanMakan is temporarily unavailable. Try again later."
        const val INVALID_RESPONSE_MESSAGE = "Sign in could not be completed. Try again."
        const val SESSION_ESTABLISHMENT_MESSAGE = "Sign in could not be completed. Try again."

        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

