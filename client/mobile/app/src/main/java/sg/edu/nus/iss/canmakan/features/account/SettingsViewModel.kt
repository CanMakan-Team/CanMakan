package sg.edu.nus.iss.canmakan.features.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.auth.data.AuthFailureType
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRepository
import sg.edu.nus.iss.canmakan.features.auth.data.AuthResult

/** Account-level settings actions. Delete always targets the signed-in user. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

    private val _deleteAccountError = MutableStateFlow<String?>(null)
    val deleteAccountError: StateFlow<String?> = _deleteAccountError.asStateFlow()

    fun deleteOwnAccount(onSuccess: () -> Unit) {
        if (_isDeletingAccount.value) return
        viewModelScope.launch {
            _isDeletingAccount.value = true
            _deleteAccountError.value = null
            when (val result = authRepository.deleteOwnAccount()) {
                is AuthResult.Success -> onSuccess()
                is AuthResult.Failure -> {
                    _deleteAccountError.value = messageFor(result.type)
                    _isDeletingAccount.value = false
                }
            }
        }
    }

    private fun messageFor(type: AuthFailureType): String {
        return when (type) {
            AuthFailureType.CONFLICT -> LAST_FAMILY_ADMIN_MESSAGE
            AuthFailureType.NETWORK -> NETWORK_MESSAGE
            else -> GENERIC_MESSAGE
        }
    }

    companion object {
        const val LAST_FAMILY_ADMIN_MESSAGE =
            "Add another family admin before deleting this account."
        const val NETWORK_MESSAGE = "Check your connection and try again."
        const val GENERIC_MESSAGE = "Your account could not be deleted. Try again."
    }
}
