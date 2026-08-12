package sg.edu.nus.iss.canmakan.testing

import com.google.gson.Gson
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionPersistence
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore

internal fun testAuthSessionStore(): AuthSessionStore =
    AuthSessionStore(InMemoryAuthSessionPersistence(), Gson())

internal fun AuthSessionStore.signInTestUser(
    userId: Long = 14L,
    email: String = "person@example.com",
): Boolean = saveSession(
    AuthenticatedSession(
        accessToken = "test-access-token-$userId",
        tokenType = "Bearer",
        expiresIn = 900,
        user = AuthenticatedUser(userId, email, AuthRole.USER),
    ),
)

private class InMemoryAuthSessionPersistence : AuthSessionPersistence {
    private var serializedSession: String? = null

    override fun readSession(): String? = serializedSession

    override fun writeSession(serializedSession: String): Boolean {
        this.serializedSession = serializedSession
        return true
    }

    override fun clearSession(): Boolean {
        serializedSession = null
        return true
    }
}
