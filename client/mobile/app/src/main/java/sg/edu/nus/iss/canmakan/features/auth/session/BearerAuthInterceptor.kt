package sg.edu.nus.iss.canmakan.features.auth.session

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the current access token to protected requests for the configured CanMakan API origin.
 *
 * This interceptor intentionally has no refresh, retry, JWT parsing, or authorization-policy
 * responsibility. It reads the session store for every request so token replacement and clearing
 * are visible without rebuilding the OkHttp client.
 */
class BearerAuthInterceptor(
    private val authSessionStore: AuthSessionStore,
    private val authRequestPolicy: AuthRequestPolicy,
) : Interceptor {
    constructor(authSessionStore: AuthSessionStore, apiBaseUrl: HttpUrl) : this(
        authSessionStore,
        AuthRequestPolicy(apiBaseUrl),
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!authRequestPolicy.isProtectedFirstParty(request) ||
            request.header(AUTHORIZATION_HEADER) != null
        ) {
            return chain.proceed(request)
        }

        val accessToken = authSessionStore.currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: return chain.proceed(request)
        val authenticatedRequest = request.newBuilder()
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX $accessToken")
            .build()
        return chain.proceed(authenticatedRequest)
    }

    override fun toString(): String {
        return "BearerAuthInterceptor(policy=$authRequestPolicy, token=<redacted>)"
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer"
    }
}
