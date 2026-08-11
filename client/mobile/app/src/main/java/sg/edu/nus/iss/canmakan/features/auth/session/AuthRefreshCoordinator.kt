package sg.edu.nus.iss.canmakan.features.auth.session

import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/** Token-bearing internal outcome whose string form never exposes the token. */
sealed interface RefreshDecision {
    class RetryWithToken(val accessToken: String) : RefreshDecision {
        override fun toString(): String = "RetryWithToken(accessToken=<redacted>)"
    }

    data object Unauthenticated : RefreshDecision

    data object Forbidden : RefreshDecision

    data object TemporarilyUnavailable : RefreshDecision

    data object InvalidResponse : RefreshDecision
}

/**
 * Serializes rotating refresh calls process-wide and publishes the new access session before
 * waiting requests are released. The lock intentionally spans only the refresh transaction.
 */
@Singleton
class AuthRefreshCoordinator @Inject constructor(
    private val refreshClient: AuthRefreshClient,
    private val authSessionStore: AuthSessionStore,
    private val refreshCookieJar: PersistentRefreshCookieJar,
    private val logoutClient: AuthLogoutClient = AuthLogoutClient {
        LogoutClientResult.INVALID_RESPONSE
    },
) {
    private val refreshLock = ReentrantLock()

    fun recoverFromUnauthorized(failedAccessToken: String): RefreshDecision {
        if (failedAccessToken.isBlank()) return RefreshDecision.Unauthenticated
        currentDecision(failedAccessToken)?.let { return it }

        return refreshLock.withLock {
            currentDecision(failedAccessToken)?.let { return@withLock it }
            performRefreshLocked()
        }
    }

    fun refreshForRestoration(): RefreshDecision {
        authSessionStore.currentAccessToken()?.let {
            return RefreshDecision.RetryWithToken(it)
        }

        return refreshLock.withLock {
            authSessionStore.currentAccessToken()
                ?.let(RefreshDecision::RetryWithToken)
                ?: performRefreshLocked()
        }
    }

    /**
     * Serializes user logout with refresh and session replacement using the same lock.
     *
     * This method is synchronous by design: callers must execute it on an IO worker. The access
     * session is removed before the backend exchange, while the refresh cookie remains available
     * until the dedicated logout request has had an opportunity to revoke it. Final local cleanup
     * is unconditional, including server, network, and unexpected failures.
     */
    fun logout() {
        refreshLock.withLock {
            authSessionStore.clearSession()
            try {
                logoutClient.logout()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                // User-requested local logout is authoritative for this device.
            } finally {
                authSessionStore.clearSession()
                refreshCookieJar.clearAuthCookies()
            }
        }
    }

    override fun toString(): String = "AuthRefreshCoordinator(state=<redacted>)"

    private fun currentDecision(failedAccessToken: String): RefreshDecision? {
        val currentAccessToken = authSessionStore.currentAccessToken()
            ?: return RefreshDecision.Unauthenticated
        return if (currentAccessToken != failedAccessToken) {
            RefreshDecision.RetryWithToken(currentAccessToken)
        } else {
            null
        }
    }

    private fun performRefreshLocked(): RefreshDecision {
        val result = try {
            refreshClient.refresh()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return RefreshDecision.TemporarilyUnavailable
        } catch (_: Exception) {
            return RefreshDecision.TemporarilyUnavailable
        }

        return when (result) {
            is RefreshClientResult.Success -> {
                if (authSessionStore.saveSession(result.session)) {
                    RefreshDecision.RetryWithToken(result.session.accessToken)
                } else {
                    clearCredentials()
                    RefreshDecision.InvalidResponse
                }
            }

            RefreshClientResult.Unauthenticated -> {
                clearCredentials()
                RefreshDecision.Unauthenticated
            }

            RefreshClientResult.Forbidden -> {
                clearCredentials()
                RefreshDecision.Forbidden
            }

            RefreshClientResult.InvalidResponse -> {
                clearCredentials()
                RefreshDecision.InvalidResponse
            }

            RefreshClientResult.NetworkFailure,
            RefreshClientResult.ServerFailure,
            -> RefreshDecision.TemporarilyUnavailable
        }
    }

    private fun clearCredentials() {
        authSessionStore.clearSession()
        refreshCookieJar.clearAuthCookies()
    }
}
