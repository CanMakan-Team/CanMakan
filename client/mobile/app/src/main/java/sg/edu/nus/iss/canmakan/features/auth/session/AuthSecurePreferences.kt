package sg.edu.nus.iss.canmakan.features.auth.session

import android.content.SharedPreferences
import javax.inject.Singleton

/** Encrypted persistence boundary used only for the authenticated-user session. */
interface AuthSessionPersistence {
    fun readSession(): String?

    fun writeSession(serializedSession: String): Boolean

    fun clearSession(): Boolean
}

/** Encrypted persistence boundary used only for the networking refresh-cookie jar. */
interface RefreshCookiePersistence {
    fun readCookies(): String?

    fun writeCookies(serializedCookies: String): Boolean

    fun clearCookies(): Boolean
}

/**
 * Session and cookie persistence used for UC19 authentication state.
 *
 * Production wiring in [sg.edu.nus.iss.canmakan.features.auth.AuthModule] supplies
 * Keystore-backed encrypted preferences. Tests supply an in-memory SharedPreferences.
 */
@Singleton
class EncryptedAuthPreferences internal constructor(
    private val preferencesFactory: () -> SharedPreferences,
) : AuthSessionPersistence, RefreshCookiePersistence {

    private val store: SharedPreferencesAuthPersistence by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SharedPreferencesAuthPersistence(preferencesFactory())
    }

    override fun readSession(): String? = store.readSession()

    override fun writeSession(serializedSession: String): Boolean = store.writeSession(serializedSession)

    override fun clearSession(): Boolean = store.clearSession()

    override fun readCookies(): String? = store.readCookies()

    override fun writeCookies(serializedCookies: String): Boolean = store.writeCookies(serializedCookies)

    override fun clearCookies(): Boolean = store.clearCookies()
}

/** Session and cookie keys stored in the encrypted preferences file. */
internal class SharedPreferencesAuthPersistence(
    private val preferences: SharedPreferences,
) : AuthSessionPersistence, RefreshCookiePersistence {

    override fun readSession(): String? = preferences.getString(SESSION_KEY, null)

    override fun writeSession(serializedSession: String): Boolean {
        return preferences.edit().putString(SESSION_KEY, serializedSession).commit()
    }

    override fun clearSession(): Boolean = preferences.edit().remove(SESSION_KEY).commit()

    override fun readCookies(): String? = preferences.getString(COOKIES_KEY, null)

    override fun writeCookies(serializedCookies: String): Boolean {
        return preferences.edit().putString(COOKIES_KEY, serializedCookies).commit()
    }

    override fun clearCookies(): Boolean = preferences.edit().remove(COOKIES_KEY).commit()
}

private const val SESSION_KEY = "authenticated_session"
private const val COOKIES_KEY = "refresh_cookies"
