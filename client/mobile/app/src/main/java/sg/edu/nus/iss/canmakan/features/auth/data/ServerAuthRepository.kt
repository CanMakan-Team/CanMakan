package sg.edu.nus.iss.canmakan.features.auth.data

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class ServerAuthRepository @Inject constructor(
    private val authApiService: AuthApiService,
) : AuthRepository {

    override suspend fun login(
        email: String,
        password: String,
    ): AuthResult<AuthenticatedSession> = safely {
        val response = authApiService.login(LoginRequest(email = email, password = password))
        when {
            response.code() == HTTP_OK -> AuthResponseValidator.validatedSession(response.body())
                ?.let { AuthResult.Success(it) }
                ?: AuthResult.Failure(AuthFailureType.INVALID_RESPONSE)

            response.code() == HTTP_BAD_REQUEST ->
                AuthResult.Failure(AuthFailureType.MALFORMED_REQUEST)

            else -> AuthResult.Failure(
                mapHttpFailure(response.code(), AuthFailureType.INVALID_CREDENTIALS)
            )
        }
    }

    override suspend fun getCurrentUser(): AuthResult<AuthenticatedUser> = safely {
        val response = authApiService.getCurrentUser()
        if (response.code() == HTTP_OK) {
            AuthResponseValidator.validatedUser(response.body())
                ?.let { AuthResult.Success(it) }
                ?: AuthResult.Failure(AuthFailureType.INVALID_RESPONSE)
        } else {
            AuthResult.Failure(
                mapHttpFailure(response.code(), AuthFailureType.UNAUTHENTICATED)
            )
        }
    }

    private suspend fun <T> safely(block: suspend () -> AuthResult<T>): AuthResult<T> {
        return try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: IOException) {
            AuthResult.Failure(AuthFailureType.NETWORK)
        } catch (_: Exception) {
            AuthResult.Failure(AuthFailureType.INVALID_RESPONSE)
        }
    }

    private fun mapHttpFailure(
        statusCode: Int,
        unauthorizedFailure: AuthFailureType,
    ): AuthFailureType {
        return when {
            statusCode == HTTP_UNAUTHORIZED -> unauthorizedFailure
            statusCode == HTTP_FORBIDDEN -> AuthFailureType.FORBIDDEN
            statusCode in HTTP_SERVER_ERROR_RANGE -> AuthFailureType.SERVER
            else -> AuthFailureType.INVALID_RESPONSE
        }
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}
