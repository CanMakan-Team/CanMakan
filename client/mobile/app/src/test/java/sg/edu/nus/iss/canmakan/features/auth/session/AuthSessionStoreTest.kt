package sg.edu.nus.iss.canmakan.features.auth.session

import com.google.gson.Gson
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser

@DisplayName("UC19 7.3: secure auth session store")
class AuthSessionStoreTest {

    @Test
    fun validSessionIsSavedLoadedAndAvailableFromTheSynchronousTokenSnapshot() {
        val persistence = FakeAuthSessionPersistence()
        val store = AuthSessionStore(persistence, Gson())

        assertTrue(store.saveSession(validSession()))

        val restored = store.loadSession()
        assertEquals(TEST_ACCESS_TOKEN, restored?.accessToken)
        assertEquals(12L, restored?.user?.userId)
        assertEquals("person@example.com", restored?.user?.email)
        assertEquals(AuthRole.USER, restored?.user?.role)
        assertEquals(TEST_ACCESS_TOKEN, store.currentAccessToken())
        assertFalse(requireNotNull(persistence.serializedSession).contains(TEST_ACCESS_TOKEN))
    }

    @Test
    fun sessionSurvivesARecreatedStoreUsingTheSameEncryptedPersistenceBoundary() {
        val persistence = FakeAuthSessionPersistence()
        assertTrue(AuthSessionStore(persistence, Gson()).saveSession(validSession()))

        val recreatedStore = AuthSessionStore(persistence, Gson())

        assertEquals(TEST_ACCESS_TOKEN, recreatedStore.currentAccessToken())
        assertEquals(AuthenticatedUser(12L, "person@example.com", AuthRole.USER), recreatedStore.loadSession()?.user)
    }

    @Test
    fun clearRemovesBothTheMemorySnapshotAndPersistedRecord() {
        val persistence = FakeAuthSessionPersistence()
        val store = AuthSessionStore(persistence, Gson())
        store.saveSession(validSession())

        assertTrue(store.clearSession())

        assertNull(store.loadSession())
        assertNull(store.currentAccessToken())
        assertNull(persistence.serializedSession)
    }

    @Test
    fun backendUserMetadataUpdatePreservesTheCurrentAccessToken() {
        val persistence = FakeAuthSessionPersistence()
        val store = AuthSessionStore(persistence, Gson())
        assertTrue(store.saveSession(validSession()))

        val authoritativeUser = AuthenticatedUser(12L, "updated@example.com", AuthRole.ADMIN)
        assertTrue(store.updateAuthenticatedUser(authoritativeUser))

        assertEquals(TEST_ACCESS_TOKEN, store.currentAccessToken())
        assertEquals(authoritativeUser, store.loadSession()?.user)
        val recreated = AuthSessionStore(persistence, Gson())
        assertEquals(TEST_ACCESS_TOKEN, recreated.currentAccessToken())
        assertEquals(authoritativeUser, recreated.loadSession()?.user)
    }

    @Test
    fun tokenFreeUserSignalTracksSaveRefreshMetadataAndClear() {
        val store = AuthSessionStore(FakeAuthSessionPersistence(), Gson())
        assertNull(store.authenticatedUser.value)

        assertTrue(store.saveSession(validSession()))
        assertEquals(
            AuthenticatedUser(12L, "person@example.com", AuthRole.USER),
            store.authenticatedUser.value,
        )

        assertTrue(store.saveSession(validSession(accessToken = "replacement-access-token")))
        assertEquals(AuthRole.USER, store.authenticatedUser.value?.role)
        assertFalse(store.authenticatedUser.value.toString().contains("replacement-access-token"))

        val authoritativeUser = AuthenticatedUser(12L, "updated@example.com", AuthRole.ADMIN)
        assertTrue(store.updateAuthenticatedUser(authoritativeUser))
        assertEquals(authoritativeUser, store.authenticatedUser.value)

        store.clearSession()
        assertNull(store.authenticatedUser.value)
    }

    @Test
    fun accountKeyIsStableForSameUserRefreshAndRenewedAcrossRealSessionBoundaries() {
        val store = AuthSessionStore(FakeAuthSessionPersistence(), Gson())
        assertTrue(store.saveSession(validSession()))
        val originalKey = requireNotNull(store.accountKey.value)

        assertTrue(store.saveSession(validSession(accessToken = "replacement-access-token")))
        assertEquals(originalKey, store.accountKey.value)

        assertTrue(store.saveSession(validSession(userId = 99L)))
        val otherAccountKey = requireNotNull(store.accountKey.value)
        assertTrue(otherAccountKey.sessionGeneration > originalKey.sessionGeneration)

        store.clearSession()
        assertNull(store.accountKey.value)
        assertTrue(store.saveSession(validSession()))
        val reloggedOriginalAccountKey = requireNotNull(store.accountKey.value)

        assertTrue(reloggedOriginalAccountKey.sessionGeneration > otherAccountKey.sessionGeneration)
        assertFalse(reloggedOriginalAccountKey == originalKey)
    }

    @Test
    fun metadataPersistenceFailureClearsTheSessionInsteadOfKeepingPartialState() {
        val persistence = FakeAuthSessionPersistence()
        val store = AuthSessionStore(persistence, Gson())
        assertTrue(store.saveSession(validSession()))
        persistence.writeSucceeds = false

        assertFalse(
            store.updateAuthenticatedUser(
                AuthenticatedUser(12L, "updated@example.com", AuthRole.ADMIN)
            )
        )

        assertNull(store.loadSession())
        assertNull(persistence.serializedSession)
    }

    @Test
    fun incompletePersistedSessionIsRejectedAndCleared() {
        val persistence = FakeAuthSessionPersistence(
            serializedSession = """{"encodedAccessToken":"dGVzdA","userId":12,"email":"person@example.com"}"""
        )

        val store = AuthSessionStore(persistence, Gson())

        assertNull(store.loadSession())
        assertTrue(persistence.clearCalls > 0)
        assertNull(persistence.serializedSession)
    }

    @Test
    fun invalidStoredRoleIsRejectedWithoutCreatingAnotherRoleEnum() {
        val persistence = FakeAuthSessionPersistence()
        AuthSessionStore(persistence, Gson()).saveSession(validSession())
        persistence.serializedSession = requireNotNull(persistence.serializedSession)
            .replace("\"USER\"", "\"PRIMARY_ADMIN\"")

        val recreatedStore = AuthSessionStore(persistence, Gson())

        assertNull(recreatedStore.loadSession())
        assertNull(persistence.serializedSession)
    }

    @Test
    fun blankTokenAndInvalidUserFieldsAreRejectedAndClearAnyStaleSession() {
        listOf(
            validSession(accessToken = " "),
            validSession(userId = 0),
            validSession(email = " "),
        ).forEach { invalidSession ->
            val persistence = FakeAuthSessionPersistence()
            val store = AuthSessionStore(persistence, Gson())
            assertTrue(store.saveSession(validSession()))

            assertFalse(store.saveSession(invalidSession))
            assertNull(store.loadSession())
            assertNull(persistence.serializedSession)
        }
    }

    @Test
    fun tokenIsRedactedFromSessionAndStoreStringRepresentations() {
        val store = AuthSessionStore(FakeAuthSessionPersistence(), Gson())
        store.saveSession(validSession())

        assertFalse(requireNotNull(store.loadSession()).toString().contains(TEST_ACCESS_TOKEN))
        assertFalse(store.toString().contains(TEST_ACCESS_TOKEN))
    }

    @Test
    fun concurrentSaveClearAndReadOperationsExposeOnlyCoherentSnapshots() {
        val store = AuthSessionStore(FakeAuthSessionPersistence(), Gson())
        val executor = Executors.newFixedThreadPool(5)
        val start = CountDownLatch(1)
        try {
            val writer = executor.submit<Boolean> {
                start.await()
                repeat(200) { index ->
                    val session = if (index % 2 == 0) {
                        validSession(accessToken = "test-token-user", userId = 12)
                    } else {
                        validSession(accessToken = "test-token-admin", userId = 99, role = AuthRole.ADMIN)
                    }
                    if (!store.saveSession(session)) return@submit false
                    if (index % 5 == 0) store.clearSession()
                }
                true
            }
            val readers = List(4) {
                executor.submit<Boolean> {
                    start.await()
                    repeat(500) {
                        val snapshot = store.loadSession()
                        if (snapshot != null) {
                            val coherent = when (snapshot.accessToken) {
                                "test-token-user" -> snapshot.user.userId == 12L && snapshot.user.role == AuthRole.USER
                                "test-token-admin" -> snapshot.user.userId == 99L && snapshot.user.role == AuthRole.ADMIN
                                else -> false
                            }
                            if (!coherent) return@submit false
                        }
                    }
                    true
                }
            }
            start.countDown()

            assertTrue(writer.get(10, TimeUnit.SECONDS))
            readers.forEach { assertTrue(it.get(10, TimeUnit.SECONDS)) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun validSession(
        accessToken: String = TEST_ACCESS_TOKEN,
        userId: Long = 12L,
        email: String = "person@example.com",
        role: AuthRole = AuthRole.USER,
    ): AuthenticatedSession {
        return AuthenticatedSession(
            accessToken = accessToken,
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(userId, email, role),
        )
    }

    private class FakeAuthSessionPersistence(
        var serializedSession: String? = null,
    ) : AuthSessionPersistence {
        var clearCalls = 0
        var writeSucceeds = true

        override fun readSession(): String? = serializedSession

        override fun writeSession(serializedSession: String): Boolean {
            if (writeSucceeds) this.serializedSession = serializedSession
            return writeSucceeds
        }

        override fun clearSession(): Boolean {
            clearCalls++
            serializedSession = null
            return true
        }
    }

    private companion object {
        const val TEST_ACCESS_TOKEN = "test-access-token"
    }
}
