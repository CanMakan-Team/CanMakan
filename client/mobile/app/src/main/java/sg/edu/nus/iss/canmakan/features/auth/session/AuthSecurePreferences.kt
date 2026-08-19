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
 * Keystore-backed encrypted preferences. Tests supply an in-memory store.
 */
@Singleton
class EncryptedAuthPreferences internal constructor(
    store: SharedPreferencesAuthPersistence,
) : AuthSessionPersistence by store, RefreshCookiePersistence by store

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
