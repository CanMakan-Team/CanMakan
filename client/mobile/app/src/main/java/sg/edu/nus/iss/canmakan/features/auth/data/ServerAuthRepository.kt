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
            response.code() == HTTP_OK -> validatedSession(response.body())
                ?.let { AuthResult.Success(it) }
                ?: AuthResult.Failure(AuthFailureType.INVALID_RESPONSE)

            response.code() == HTTP_BAD_REQUEST ->
                AuthResult.Failure(AuthFailureType.MALFORMED_REQUEST)

            else -> AuthResult.Failure(
                mapHttpFailure(response.code(), AuthFailureType.INVALID_CREDENTIALS)
            )
        }
    }

    override suspend fun refresh(): AuthResult<AuthenticatedSession> = safely {
        val response = authApiService.refresh()
        if (response.code() == HTTP_OK) {
            validatedSession(response.body())
                ?.let { AuthResult.Success(it) }
                ?: AuthResult.Failure(AuthFailureType.INVALID_RESPONSE)
        } else {
            AuthResult.Failure(
                mapHttpFailure(response.code(), AuthFailureType.UNAUTHENTICATED)
            )
        }
    }

    override suspend fun logout(): AuthResult<Unit> = safely {
        val response = authApiService.logout()
        if (response.code() == HTTP_NO_CONTENT) {
            AuthResult.Success(Unit)
        } else {
            AuthResult.Failure(
                mapHttpFailure(response.code(), AuthFailureType.UNAUTHENTICATED)
            )
        }
    }

    override suspend fun getCurrentUser(): AuthResult<AuthenticatedUser> = safely {
        val response = authApiService.getCurrentUser()
        if (response.code() == HTTP_OK) {
            validatedUser(response.body())
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

    private fun validatedSession(response: AuthResponse?): AuthenticatedSession? {
        response ?: return null
        val accessToken = response.accessToken?.takeIf { it.isNotBlank() } ?: return null
        if (!response.tokenType.equals(TOKEN_TYPE_BEARER, ignoreCase = true)) return null
        val expiresIn = response.expiresIn?.takeIf { it > 0 } ?: return null
        val user = validatedUser(response.user) ?: return null

        return AuthenticatedSession(
            accessToken = accessToken,
            tokenType = TOKEN_TYPE_BEARER,
            expiresIn = expiresIn,
            user = user,
        )
    }

    private fun validatedUser(response: AuthenticatedUserResponse?): AuthenticatedUser? {
        response ?: return null
        val userId = response.userId?.takeIf { it > 0 } ?: return null
        val email = response.email?.takeIf { it.isNotBlank() } ?: return null
        val role = response.role ?: return null

        return AuthenticatedUser(userId = userId, email = email, role = role)
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
        const val HTTP_NO_CONTENT = 204
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        val HTTP_SERVER_ERROR_RANGE = 500..599
        const val TOKEN_TYPE_BEARER = "Bearer"
    }
}
