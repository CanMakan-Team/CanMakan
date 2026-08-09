package sg.edu.nus.iss.canmakan.features.auth.data

/** Shared validation for login and refresh responses from the frozen Backend contract. */
internal object AuthResponseValidator {
    fun validatedSession(response: AuthResponse?): AuthenticatedSession? {
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

    fun validatedUser(response: AuthenticatedUserResponse?): AuthenticatedUser? {
        response ?: return null
        val userId = response.userId?.takeIf { it > 0 } ?: return null
        val email = response.email?.takeIf { it.isNotBlank() } ?: return null
        val role = response.role ?: return null

        return AuthenticatedUser(userId = userId, email = email, role = role)
    }

    private const val TOKEN_TYPE_BEARER = "Bearer"
}
