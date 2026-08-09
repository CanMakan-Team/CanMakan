package sg.edu.nus.iss.canmakan.features.auth.session

import com.google.gson.Gson
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser

/** Immutable authenticated state restored from encrypted persistence. */
data class AuthSessionSnapshot(
    val accessToken: String,
    val user: AuthenticatedUser,
) {
    override fun toString(): String {
        return "AuthSessionSnapshot(accessToken=<redacted>, user=$user)"
    }
}

/**
 * Owns the access token and authoritative backend user summary.
 *
 * Persistence is read once when the component is created. Subsequent token reads use the
 * synchronized in-memory snapshot so a future OkHttp interceptor will not read disk per request.
 */
@Singleton
class AuthSessionStore @Inject constructor(
    private val persistence: AuthSessionPersistence,
    private val gson: Gson,
) {
    private val lock = Any()
    private var currentSession: AuthSessionSnapshot? = restoreSession()

    fun saveSession(session: AuthenticatedSession): Boolean = synchronized(lock) {
        val snapshot = validatedSnapshot(session.accessToken, session.user)
        if (snapshot == null) {
            clearLocked()
            return@synchronized false
        }
        persistSnapshotLocked(snapshot)
    }

    fun loadSession(): AuthSessionSnapshot? = synchronized(lock) { currentSession }

    fun currentAccessToken(): String? = synchronized(lock) { currentSession?.accessToken }

    /** Replaces Backend-authoritative user metadata while preserving the latest access token. */
    fun updateAuthenticatedUser(user: AuthenticatedUser): Boolean = synchronized(lock) {
        val session = currentSession ?: return@synchronized false
        val updated = validatedSnapshot(session.accessToken, user)
        if (updated == null) {
            clearLocked()
            return@synchronized false
        }
        persistSnapshotLocked(updated)
    }

    fun clearSession(): Boolean = synchronized(lock) { clearLocked() }

    override fun toString(): String = "AuthSessionStore(session=<redacted>)"

    private fun restoreSession(): AuthSessionSnapshot? {
        val persistedSession = runCatching { persistence.readSession() }
        if (persistedSession.isFailure) {
            clearPersistenceBestEffort()
            return null
        }
        val serialized = persistedSession.getOrNull() ?: return null
        val record = runCatching {
            gson.fromJson(serialized, StoredSessionRecord::class.java)
        }.getOrNull()
        val snapshot = record?.toSnapshot()
        if (snapshot == null) {
            clearPersistenceBestEffort()
        }
        return snapshot
    }

    private fun StoredSessionRecord.toSnapshot(): AuthSessionSnapshot? {
        val accessToken = encodedAccessToken?.let(::decodeSecret)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val validUserId = userId?.takeIf { it > 0 } ?: return null
        val validEmail = email?.takeIf { it.isNotBlank() } ?: return null
        val validRole = role?.let { storedRole ->
            AuthRole.entries.singleOrNull { it.name == storedRole }
        } ?: return null

        return validatedSnapshot(
            accessToken,
            AuthenticatedUser(validUserId, validEmail, validRole),
        )
    }

    private fun validatedSnapshot(
        accessToken: String,
        user: AuthenticatedUser,
    ): AuthSessionSnapshot? {
        if (accessToken.isBlank()) return null
        if (user.userId <= 0) return null
        if (user.email.isBlank()) return null
        return AuthSessionSnapshot(accessToken = accessToken, user = user)
    }

    private fun clearLocked(): Boolean {
        currentSession = null
        return runCatching { persistence.clearSession() }.getOrDefault(false)
    }

    private fun persistSnapshotLocked(snapshot: AuthSessionSnapshot): Boolean {
        val record = StoredSessionRecord(
            encodedAccessToken = encodeSecret(snapshot.accessToken),
            userId = snapshot.user.userId,
            email = snapshot.user.email,
            role = snapshot.user.role.name,
        )
        val serialized = runCatching { gson.toJson(record) }.getOrNull()
        val saved = serialized != null && runCatching {
            persistence.writeSession(serialized)
        }.getOrDefault(false)

        if (saved) {
            currentSession = snapshot
        } else {
            currentSession = null
            clearPersistenceBestEffort()
        }
        return saved
    }

    private fun clearPersistenceBestEffort() {
        runCatching { persistence.clearSession() }
    }

    private class StoredSessionRecord(
        val encodedAccessToken: String?,
        val userId: Long?,
        val email: String?,
        val role: String?,
    ) {
        override fun toString(): String {
            return "StoredSessionRecord(encodedAccessToken=<redacted>, userId=$userId, " +
                "email=$email, role=$role)"
        }
    }

    private companion object {
        fun encodeSecret(value: String): String {
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        }

        fun decodeSecret(value: String): String? = runCatching {
            val decoded = Base64.getUrlDecoder().decode(value)
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(decoded))
                .toString()
        }.getOrNull()
    }
}
