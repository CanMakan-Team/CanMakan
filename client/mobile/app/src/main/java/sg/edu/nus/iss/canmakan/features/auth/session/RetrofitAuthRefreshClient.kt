package sg.edu.nus.iss.canmakan.features.auth.session

import java.io.IOException
import sg.edu.nus.iss.canmakan.features.auth.data.AuthResponseValidator
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.RefreshApiService

/** Result of one non-recursive refresh HTTP exchange. */
sealed interface RefreshClientResult {
    class Success(val session: AuthenticatedSession) : RefreshClientResult {
        override fun toString(): String = "Success(session=<redacted>)"
    }

    data object Unauthenticated : RefreshClientResult

    data object Forbidden : RefreshClientResult

    data object NetworkFailure : RefreshClientResult

    data object ServerFailure : RefreshClientResult

    data object InvalidResponse : RefreshClientResult
}

/** Synchronous boundary used by the single-flight coordinator. */
fun interface AuthRefreshClient {
    fun refresh(): RefreshClientResult
}

/** Token-free outcome of one best-effort backend logout exchange. */
enum class LogoutClientResult {
    SUCCESS,
    NETWORK_FAILURE,
    SERVER_FAILURE,
    INVALID_RESPONSE,
}

/** Synchronous logout boundary sharing the dedicated refresh network and CookieJar. */
fun interface AuthLogoutClient {
    fun logout(): LogoutClientResult
}

/** Executes refresh on the dedicated Retrofit service, outside the main OkHttp graph. */
class RetrofitAuthRefreshClient(
    private val refreshApiService: RefreshApiService,
) : AuthRefreshClient {
    override fun refresh(): RefreshClientResult {
        return try {
            val response = refreshApiService.refresh().execute()
            when {
                response.code() == HTTP_OK -> {
                    AuthResponseValidator.validatedSession(response.body())
                        ?.let(RefreshClientResult::Success)
                        ?: RefreshClientResult.InvalidResponse
                }

                response.code() == HTTP_UNAUTHORIZED -> RefreshClientResult.Unauthenticated
                response.code() == HTTP_FORBIDDEN -> RefreshClientResult.Forbidden
                response.code() in HTTP_SERVER_ERROR_RANGE -> RefreshClientResult.ServerFailure
                else -> RefreshClientResult.InvalidResponse
            }
        } catch (_: IOException) {
            RefreshClientResult.NetworkFailure
        } catch (_: Exception) {
            RefreshClientResult.InvalidResponse
        }
    }

    override fun toString(): String = "RetrofitAuthRefreshClient(service=<redacted>)"

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}

/** Executes logout without Bearer authentication, Authenticator recursion, or generic retries. */
class RetrofitAuthLogoutClient(
    private val refreshApiService: RefreshApiService,
) : AuthLogoutClient {
    override fun logout(): LogoutClientResult {
        return try {
            val response = refreshApiService.logout().execute()
            when {
                response.code() == HTTP_NO_CONTENT -> LogoutClientResult.SUCCESS
                response.code() in HTTP_SERVER_ERROR_RANGE -> LogoutClientResult.SERVER_FAILURE
                else -> LogoutClientResult.INVALID_RESPONSE
            }
        } catch (_: IOException) {
            LogoutClientResult.NETWORK_FAILURE
        } catch (_: Exception) {
            LogoutClientResult.INVALID_RESPONSE
        }
    }

    override fun toString(): String = "RetrofitAuthLogoutClient(service=<redacted>)"

    private companion object {
        const val HTTP_NO_CONTENT = 204
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}
