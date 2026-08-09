package sg.edu.nus.iss.canmakan.features.auth.session

import okhttp3.HttpUrl
import okhttp3.Request

/** Defines the exact first-party request scope shared by Bearer auth and 401 recovery. */
class AuthRequestPolicy(apiBaseUrl: HttpUrl) {
    private val apiOrigin = ApiOrigin.from(apiBaseUrl)
    private val publicEndpoints = setOf(
        Endpoint("POST", requireNotNull(apiBaseUrl.resolve("auth/register")).encodedPath),
        Endpoint("POST", requireNotNull(apiBaseUrl.resolve("auth/login")).encodedPath),
        Endpoint("POST", requireNotNull(apiBaseUrl.resolve("auth/refresh")).encodedPath),
        Endpoint("POST", requireNotNull(apiBaseUrl.resolve("auth/logout")).encodedPath),
        Endpoint("GET", HEALTH_PATH),
    )

    fun isProtectedFirstParty(request: Request): Boolean {
        return apiOrigin.matches(request.url) &&
            Endpoint(request.method, request.url.encodedPath) !in publicEndpoints
    }

    override fun toString(): String = "AuthRequestPolicy(apiOrigin=${apiOrigin.displayValue})"

    private data class Endpoint(
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
        const val HEALTH_PATH = "/actuator/health"
    }
}
