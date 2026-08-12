package sg.edu.nus.iss.canmakan.features.auth.onboarding

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory hand-off from account registration to authenticated profile setup.
 *
 * This deliberately stores no password, user id, or authentication material. It survives normal
 * registration-to-login navigation, but not process death; users can configure a profile later.
 */
data class PendingDietaryOnboarding(
    val accountEmail: String,
    val requestId: Long,
)

@Singleton
class PendingOnboardingStore @Inject constructor() {
    private var nextRequestId = 1L
    private val _dietaryOnboarding = MutableStateFlow<PendingDietaryOnboarding?>(null)
    val dietaryOnboarding: StateFlow<PendingDietaryOnboarding?> =
        _dietaryOnboarding.asStateFlow()

    @Synchronized
    fun requestDietarySetup(accountEmail: String) {
        val normalizedEmail = normalizeEmail(accountEmail)
        require(normalizedEmail.isNotEmpty()) { "Pending onboarding requires an account email." }
        _dietaryOnboarding.value = PendingDietaryOnboarding(
            accountEmail = normalizedEmail,
            requestId = nextRequestId++,
        )
    }

    @Synchronized
    fun peek(): PendingDietaryOnboarding? = _dietaryOnboarding.value

    /** Returns this intent only to its registered account; a mismatch invalidates stale state. */
    @Synchronized
    fun peekForAccount(accountEmail: String): PendingDietaryOnboarding? {
        val pending = _dietaryOnboarding.value ?: return null
        if (pending.accountEmail != normalizeEmail(accountEmail)) {
            _dietaryOnboarding.value = null
            return null
        }
        return pending
    }

    /** Prevents an old async result from clearing a newer account's onboarding intent. */
    @Synchronized
    fun clearForAccount(accountEmail: String) {
        if (_dietaryOnboarding.value?.accountEmail == normalizeEmail(accountEmail)) {
            _dietaryOnboarding.value = null
        }
    }

    /** Clears only the exact intent that an operation originally observed. */
    @Synchronized
    fun clearIfCurrent(pending: PendingDietaryOnboarding) {
        if (_dietaryOnboarding.value?.requestId == pending.requestId) {
            _dietaryOnboarding.value = null
        }
    }

    @Synchronized
    fun isCurrent(pending: PendingDietaryOnboarding): Boolean =
        _dietaryOnboarding.value?.requestId == pending.requestId

    @Synchronized
    fun clear() {
        _dietaryOnboarding.value = null
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)
}
