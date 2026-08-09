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
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationFailureType
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationRepository
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationResponse
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationResult
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager

enum class RegistrationStep {
    ACCOUNT_INFORMATION,
    OPTIONAL_DIETARY_PROFILE,
    COMPLETE,
}

enum class ProfileSetupStatus {
    NOT_REQUESTED,
    SELECTED,
    DEFERRED_UNAVAILABLE,
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
    val availableRestrictions: List<DietaryRestriction> = emptyList(),
    val selectedRestrictionIds: Set<Long> = emptySet(),
    val dietaryOptionsLoading: Boolean = false,
    val dietaryOptionsError: String? = null,
    val isSubmitting: Boolean = false,
    val registrationError: String? = null,
    val registrationFailureType: RegistrationFailureType? = null,
    val account: RegistrationResponse? = null,
    val profileSetupStatus: ProfileSetupStatus = ProfileSetupStatus.NOT_REQUESTED,
    val profileSetupMessage: String? = null,
) {
    override fun toString(): String {
        return "RegistrationUiState(name=$name, email=$email, password=<redacted>, " +
            "confirmPassword=<redacted>, step=$step, isSubmitting=$isSubmitting, " +
            "accountCreated=${account != null}, profileSetupStatus=$profileSetupStatus)"
    }
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val registrationRepository: RegistrationRepository,
    private val dietaryRestrictionRepository: DietaryRestrictionRepository,
    private val activeProfileManager: ActiveProfileManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    init {
        loadDietaryOptions()
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

    fun continueToDietaryProfile() {
        val state = _uiState.value
        val normalizedName = state.name.trim()
        val nameError = when {
            normalizedName.isEmpty() -> "Name is required."
            normalizedName.length < MIN_NAME_LENGTH -> "Name must be at least 3 characters."
            normalizedName.length > MAX_NAME_LENGTH ->
                "Name must be between 3 and 100 characters."
            else -> null
        }
        val normalizedEmail = state.email.trim()
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

        _uiState.value = state.copy(
            nameError = nameError,
            emailError = emailError,
            passwordError = passwordError,
            confirmPasswordError = confirmPasswordError,
            registrationError = null,
            registrationFailureType = null,
            step = if (nameError == null && emailError == null &&
                passwordError == null && confirmPasswordError == null
            ) {
                RegistrationStep.OPTIONAL_DIETARY_PROFILE
            } else {
                RegistrationStep.ACCOUNT_INFORMATION
            },
        )
    }

    fun backToAccountInformation() {
        if (_uiState.value.account == null && !_uiState.value.isSubmitting) {
            _uiState.value = _uiState.value.copy(step = RegistrationStep.ACCOUNT_INFORMATION)
        }
    }

    fun toggleRestriction(restrictionId: Long) {
        val state = _uiState.value
        if (state.account != null || state.isSubmitting) return

        val selected = state.selectedRestrictionIds.toMutableSet()
        if (!selected.add(restrictionId)) {
            selected.remove(restrictionId)
        } else {
            val selectedRestriction = state.availableRestrictions.firstOrNull {
                it.id == restrictionId
            }
            if (selectedRestriction?.category == RELIGIOUS_CATEGORY) {
                state.availableRestrictions
                    .filter { it.category == RELIGIOUS_CATEGORY && it.id != restrictionId }
                    .forEach { selected.remove(it.id) }
            }
        }

        _uiState.value = state.copy(
            selectedRestrictionIds = selected,
            profileSetupStatus = if (selected.isEmpty()) {
                ProfileSetupStatus.NOT_REQUESTED
            } else {
                ProfileSetupStatus.SELECTED
            },
        )
    }

    fun createAccount() {
        val state = _uiState.value
        if (state.step != RegistrationStep.OPTIONAL_DIETARY_PROFILE ||
            state.isSubmitting || state.account != null
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                registrationError = null,
                registrationFailureType = null,
            )

            when (val result = registrationRepository.register(
                name = state.name.trim(),
                email = state.email.trim().lowercase(Locale.ROOT),
                password = state.password,
            )) {
                is RegistrationResult.Success -> handleAccountCreated(result.account)
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

    fun completeProfileSetupLater() {
        val state = _uiState.value
        if (state.account != null && state.profileSetupStatus == ProfileSetupStatus.DEFERRED_UNAVAILABLE) {
            _uiState.value = state.copy(step = RegistrationStep.COMPLETE)
        }
    }

    private suspend fun handleAccountCreated(account: RegistrationResponse) {
        persistLocalProfile(account)
        val state = _uiState.value
        if (state.selectedRestrictionIds.isEmpty()) {
            _uiState.value = state.copy(
                step = RegistrationStep.COMPLETE,
                isSubmitting = false,
                password = "",
                confirmPassword = "",
                account = account,
                profileSetupStatus = ProfileSetupStatus.NOT_REQUESTED,
            )
            return
        }

        // Attempt to persist the selected restrictions onto the profile that
        // was just created alongside the account. Only fall back to the
        // deferred message if this actually fails, e.g. a network error —
        // not merely because restrictions were selected.
        val selections = state.selectedRestrictionIds.associateWith { DEFAULT_SEVERITY_LEVEL }
        val saved = try {
            dietaryRestrictionRepository.saveDietaryRestrictionSelections(account.profileId, selections)
        } catch (_: Exception) {
            false
        }

        _uiState.value = if (saved) {
            state.copy(
                step = RegistrationStep.COMPLETE,
                isSubmitting = false,
                password = "",
                confirmPassword = "",
                account = account,
                profileSetupStatus = ProfileSetupStatus.SELECTED,
            )
        } else {
            state.copy(
                isSubmitting = false,
                password = "",
                confirmPassword = "",
                account = account,
                profileSetupStatus = ProfileSetupStatus.DEFERRED_UNAVAILABLE,
                profileSetupMessage = PROFILE_SETUP_DEFERRED_MESSAGE,
            )
        }
    }

    private fun persistLocalProfile(account: RegistrationResponse) {
        // Registration does not establish UC19 auth; login owns the session.
        activeProfileManager.switchProfile(account.profileId)
    }

    private fun loadDietaryOptions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                dietaryOptionsLoading = true,
                dietaryOptionsError = null,
            )
            try {
                val restrictions = dietaryRestrictionRepository.getAllDietaryRestrictions()
                _uiState.value = _uiState.value.copy(availableRestrictions = restrictions)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    dietaryOptionsError =
                        "Dietary options are unavailable. You can set them up later.",
                )
            } finally {
                _uiState.value = _uiState.value.copy(dietaryOptionsLoading = false)
            }
        }
    }

    companion object {
        const val PROFILE_SETUP_DEFERRED_MESSAGE =
            "Your account was created, but profile setup could not be completed."

        private const val MAX_EMAIL_LENGTH = 255
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_UTF8_BYTES = 72
        private const val MIN_NAME_LENGTH = 3
        private const val MAX_NAME_LENGTH = 100
        private const val RELIGIOUS_CATEGORY = "RELIGIOUS"
        private const val DEFAULT_SEVERITY_LEVEL = "STRICT_AVOID"
        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        private const val PASSWORD_STRENGTH_MESSAGE =
            "Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character."

        private fun utf8ByteLength(value: String): Int =
            value.toByteArray(Charsets.UTF_8).size

        private fun meetsRegistrationPasswordPolicy(password: String): Boolean {
            if (password.length < MIN_PASSWORD_LENGTH) {
                return false
            }
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
