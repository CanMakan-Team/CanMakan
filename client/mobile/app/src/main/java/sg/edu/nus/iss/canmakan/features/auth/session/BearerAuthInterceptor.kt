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
    apiBaseUrl: HttpUrl,
) : Interceptor {
    private val apiOrigin = ApiOrigin.from(apiBaseUrl)
    private val publicEndpoints = setOf(
        PublicEndpoint("POST", requireNotNull(apiBaseUrl.resolve("auth/register")).encodedPath),
        PublicEndpoint("POST", requireNotNull(apiBaseUrl.resolve("auth/login")).encodedPath),
        PublicEndpoint("POST", requireNotNull(apiBaseUrl.resolve("auth/refresh")).encodedPath),
        PublicEndpoint("POST", requireNotNull(apiBaseUrl.resolve("auth/logout")).encodedPath),
        PublicEndpoint("GET", HEALTH_PATH),
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!apiOrigin.matches(request.url) ||
            PublicEndpoint(request.method, request.url.encodedPath) in publicEndpoints ||
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
        return "BearerAuthInterceptor(apiOrigin=${apiOrigin.displayValue}, token=<redacted>)"
    }

    private data class PublicEndpoint(
        val method: String,
        val encodedPath: String,
    )

    private data class ApiOrigin(
        val scheme: String,
        val host: String,
        val port: Int,
    ) {
        val displayValue: String = "$scheme://$host:$port"

        fun matches(url: HttpUrl): Boolean {
            return url.scheme == scheme && url.host == host && url.port == port
        }

        companion object {
            fun from(url: HttpUrl): ApiOrigin {
                return ApiOrigin(url.scheme, url.host, url.port)
            }
        }
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer"
        const val HEALTH_PATH = "/actuator/health"
    }
}
