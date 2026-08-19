package sg.edu.nus.iss.canmakan.features.auth.session

import android.content.SharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthSecurePreferencesTest {

    @Test
    fun encryptedPreferencesDelegatesSessionAndCookies() {
        val preferences = EncryptedAuthPreferences { MemorySharedPreferences() }

        assertNull(preferences.readSession())
        assertNull(preferences.readCookies())
        assertTrue(preferences.writeSession("""{"userId":12}"""))
        assertTrue(preferences.writeCookies("cookie-jar"))
        assertEquals("""{"userId":12}""", preferences.readSession())
        assertEquals("cookie-jar", preferences.readCookies())
        assertTrue(preferences.clearCookies())
        assertEquals("""{"userId":12}""", preferences.readSession())
        assertNull(preferences.readCookies())
        assertTrue(preferences.clearSession())
        assertNull(preferences.readSession())
    }

    @Test
    fun sessionRoundTripAndClear() {
        val store = SharedPreferencesAuthPersistence(MemorySharedPreferences())

        assertNull(store.readSession())
        assertTrue(store.writeSession("""{"userId":12}"""))
        assertEquals("""{"userId":12}""", store.readSession())
        assertTrue(store.clearSession())
        assertNull(store.readSession())
    }

    @Test
    fun cookiesAreIndependentOfSession() {
        val store = SharedPreferencesAuthPersistence(MemorySharedPreferences())

        assertTrue(store.writeSession("session"))
        assertTrue(store.writeCookies("cookie-jar"))
        assertEquals("session", store.readSession())
        assertEquals("cookie-jar", store.readCookies())
        assertTrue(store.clearCookies())
        assertEquals("session", store.readSession())
        assertNull(store.readCookies())
    }

    private class MemorySharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, String>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? =
            values[key] ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            defValues

        override fun getInt(key: String?, defValue: Int): Int = defValue

        override fun getLong(key: String?, defValue: Long): Long = defValue

        override fun getFloat(key: String?, defValue: Float): Float = defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue

        override fun contains(key: String?): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = MemoryEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        private inner class MemoryEditor : SharedPreferences.Editor {
            private val pendingPuts = mutableMapOf<String, String>()
            private val pendingRemoves = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) {
                    if (value == null) pendingRemoves += key else pendingPuts[key] = value
                }
                return this
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ): SharedPreferences.Editor = this

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pendingRemoves += key
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) values.clear()
                pendingRemoves.forEach(values::remove)
                values.putAll(pendingPuts)
            }
        }
    }
}
