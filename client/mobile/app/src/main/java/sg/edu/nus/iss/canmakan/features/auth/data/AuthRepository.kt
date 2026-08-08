package sg.edu.nus.iss.canmakan.features.auth.data

data class AuthenticatedUser(
    val userId: Long,
    val email: String,
    val role: AuthRole,
)

data class AuthenticatedSession(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: AuthenticatedUser,
) {
    override fun toString(): String {
        return "AuthenticatedSession(accessToken=<redacted>, tokenType=$tokenType, " +
            "expiresIn=$expiresIn, user=$user)"
    }
}

enum class AuthFailureType {
    MALFORMED_REQUEST,
    INVALID_CREDENTIALS,
    UNAUTHENTICATED,
    FORBIDDEN,
    SERVER,
    NETWORK,
    INVALID_RESPONSE,
}

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>

    data class Failure(val type: AuthFailureType) : AuthResult<Nothing>
}

interface AuthRepository {
    suspend fun login(email: String, password: String): AuthResult<AuthenticatedSession>

    suspend fun refresh(): AuthResult<AuthenticatedSession>

    suspend fun logout(): AuthResult<Unit>

    suspend fun getCurrentUser(): AuthResult<AuthenticatedUser>
}
