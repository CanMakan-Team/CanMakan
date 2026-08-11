@file:Suppress("DEPRECATION")

package sg.edu.nus.iss.canmakan.features.auth.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
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
 * Keystore-backed encrypted storage for UC19 authentication state.
 *
 * AndroidX Security Crypto 1.1.0 deprecates this API but does not provide a replacement encrypted
 * preferences implementation. Keeping the existing dependency avoids introducing custom crypto;
 * keys and values are encrypted and the master key is held by Android Keystore.
 */
@Singleton
class EncryptedAuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : AuthSessionPersistence, RefreshCookiePersistence {

    private val preferences: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFERENCES_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

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

    private companion object {
        const val PREFERENCES_FILE = "canmakan_auth_secure"
        const val SESSION_KEY = "authenticated_session"
        const val COOKIES_KEY = "refresh_cookies"
    }
}
