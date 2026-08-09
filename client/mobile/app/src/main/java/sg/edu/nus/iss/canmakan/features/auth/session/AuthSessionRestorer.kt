package sg.edu.nus.iss.canmakan.features.auth.session

import okhttp3.HttpUrl
import sg.edu.nus.iss.canmakan.features.auth.data.AuthFailureType
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRepository
import sg.edu.nus.iss.canmakan.features.auth.data.AuthResult
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser

/** Token-safe result for the future root authentication flow. */
sealed interface AuthRestorationResult {
    data class Authenticated(val user: AuthenticatedUser) : AuthRestorationResult

    data object Unauthenticated : AuthRestorationResult

    data object TemporarilyUnavailable : AuthRestorationResult

    data object Forbidden : AuthRestorationResult
}

/** Restores authentication state without making navigation or UI decisions. */
class AuthSessionRestorer(
    private val authRepository: AuthRepository,
    private val authSessionStore: AuthSessionStore,
    private val refreshCoordinator: AuthRefreshCoordinator,
    private val refreshCookieJar: PersistentRefreshCookieJar,
    private val refreshUrl: HttpUrl,
) {
    suspend fun restore(): AuthRestorationResult {
        val initialSession = authSessionStore.loadSession()
        if (initialSession == null) {
            if (!refreshCookieJar.hasAuthCookieFor(refreshUrl)) {
                return AuthRestorationResult.Unauthenticated
            }
            return restoreFromRefreshCookie()
        }

        return restoreFromCurrentUser(initialSession.accessToken)
    }

    override fun toString(): String = "AuthSessionRestorer(state=<redacted>)"

    private suspend fun restoreFromCurrentUser(
        initialAccessToken: String,
    ): AuthRestorationResult {
        return when (val result = authRepository.getCurrentUser()) {
            is AuthResult.Success -> synchronizeCurrentUser(result.value)
            is AuthResult.Failure -> restorationFailure(result.type, initialAccessToken)
        }
    }

    private fun restoreFromRefreshCookie(): AuthRestorationResult {
        return when (refreshCoordinator.refreshForRestoration()) {
            is RefreshDecision.RetryWithToken -> {
                val user = authSessionStore.loadSession()?.user
                if (user != null) {
                    AuthRestorationResult.Authenticated(user)
                } else {
                    clearCredentials()
                    AuthRestorationResult.Unauthenticated
                }
            }

            RefreshDecision.Unauthenticated,
            RefreshDecision.InvalidResponse,
            -> AuthRestorationResult.Unauthenticated

            RefreshDecision.Forbidden -> AuthRestorationResult.Forbidden
            RefreshDecision.TemporarilyUnavailable ->
                AuthRestorationResult.TemporarilyUnavailable
        }
    }

    private fun synchronizeCurrentUser(user: AuthenticatedUser): AuthRestorationResult {
        return if (authSessionStore.updateAuthenticatedUser(user)) {
            AuthRestorationResult.Authenticated(user)
        } else {
            clearCredentials()
            AuthRestorationResult.Unauthenticated
        }
    }

    private fun restorationFailure(
        failureType: AuthFailureType,
        initialAccessToken: String,
    ): AuthRestorationResult {
        return when (failureType) {
            AuthFailureType.UNAUTHENTICATED -> {
                val currentAccessToken = authSessionStore.currentAccessToken()
                when {
                    currentAccessToken == null -> AuthRestorationResult.Unauthenticated
                    currentAccessToken != initialAccessToken -> {
                        clearCredentials()
                        AuthRestorationResult.Unauthenticated
                    }

                    else -> AuthRestorationResult.TemporarilyUnavailable
                }
            }

            AuthFailureType.FORBIDDEN -> AuthRestorationResult.Forbidden
            AuthFailureType.NETWORK,
            AuthFailureType.SERVER,
            AuthFailureType.INVALID_RESPONSE,
            AuthFailureType.MALFORMED_REQUEST,
            AuthFailureType.INVALID_CREDENTIALS,
            -> AuthRestorationResult.TemporarilyUnavailable
        }
    }

    private fun clearCredentials() {
        authSessionStore.clearSession()
        refreshCookieJar.clearAuthCookies()
    }
}
