package sg.edu.nus.iss.canmakan.features.family.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds an invite token extracted from an Android deep-link Intent so it can be
 * claimed after login or when the authenticated shell is already showing.
 */
@Singleton
class PendingInvitationStore @Inject constructor() {

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    fun offer(token: String?) {
        val trimmed = token?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            _token.value = trimmed
        }
    }

    fun peek(): String? = _token.value

    fun consume(): String? {
        val current = _token.value
        _token.value = null
        return current
    }

    fun clear() {
        _token.value = null
    }
}
