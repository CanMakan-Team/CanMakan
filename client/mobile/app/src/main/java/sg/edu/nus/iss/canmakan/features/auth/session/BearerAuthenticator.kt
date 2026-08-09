package sg.edu.nus.iss.canmakan.features.auth.session

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/** Performs one origin-scoped Bearer recovery attempt for an eligible HTTP 401 response. */
@Singleton
class BearerAuthenticator @Inject constructor(
    private val authRequestPolicy: AuthRequestPolicy,
    private val refreshCoordinator: AuthRefreshCoordinator,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val failedRequest = response.request
        if (response.code != HTTP_UNAUTHORIZED) return null
        if (!authRequestPolicy.isProtectedFirstParty(failedRequest)) return null
        if (failedRequest.tag(AuthenticationRetryMarker::class.java) != null) return null

        val failedAccessToken = bearerToken(failedRequest) ?: return null
        val decision = refreshCoordinator.recoverFromUnauthorized(failedAccessToken)
        if (decision !is RefreshDecision.RetryWithToken) return null

        return failedRequest.newBuilder()
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX ${decision.accessToken}")
            .tag(AuthenticationRetryMarker::class.java, AUTHENTICATION_RETRY_MARKER)
            .build()
    }

    override fun toString(): String {
        return "BearerAuthenticator(policy=$authRequestPolicy, coordinator=<redacted>)"
    }

    private fun bearerToken(request: Request): String? {
        val authorizationValues = request.headers.values(AUTHORIZATION_HEADER)
        if (authorizationValues.size != 1) return null
        val parts = authorizationValues.single().trim().split(WHITESPACE, limit = 3)
        if (parts.size != 2 || !parts[0].equals(BEARER_PREFIX, ignoreCase = true)) return null
        return parts[1].takeIf { it.isNotBlank() }
    }

    private class AuthenticationRetryMarker

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer"
        val WHITESPACE = Regex("\\s+")
        val AUTHENTICATION_RETRY_MARKER = AuthenticationRetryMarker()
    }
}
