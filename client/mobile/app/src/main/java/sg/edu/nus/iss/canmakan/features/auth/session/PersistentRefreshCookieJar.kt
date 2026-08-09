package sg.edu.nus.iss.canmakan.features.auth.session

import com.google.gson.Gson
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Thread-safe, encrypted-persistence CookieJar restricted to CanMakan's refresh cookie.
 *
 * Cookie values never have an application-facing getter. OkHttp receives them only through the
 * standard CookieJar contract after its own host, path and Secure matching succeeds.
 */
@Singleton
class PersistentRefreshCookieJar internal constructor(
    private val persistence: RefreshCookiePersistence,
    private val gson: Gson,
    private val currentTimeMillis: () -> Long,
) : CookieJar {

    @Inject
    constructor(
        persistence: RefreshCookiePersistence,
        gson: Gson,
    ) : this(persistence, gson, System::currentTimeMillis)

    private val lock = Any()
    private var cookiesByIdentity = restoreCookies()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val refreshCookies = cookies.filter { it.name == REFRESH_COOKIE_NAME }
        if (refreshCookies.isEmpty()) return

        synchronized(lock) {
            val now = currentTimeMillis()
            val updated = LinkedHashMap(cookiesByIdentity)
            refreshCookies.forEach { cookie ->
                val identity = CookieIdentity(cookie.name, cookie.domain, cookie.path)
                if (cookie.expiresAt <= now) {
                    updated.remove(identity)
                } else {
                    updated[identity] = cookie
                }
            }
            cookiesByIdentity = updated
            persistCurrentCookiesLocked(now)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = currentTimeMillis()
        removeExpiredCookiesLocked(now)
        cookiesByIdentity.values.filter { cookie -> cookie.matches(url) }
    }

    /** Reports usable refresh-cookie presence without exposing its value outside the CookieJar. */
    fun hasAuthCookieFor(url: HttpUrl): Boolean = synchronized(lock) {
        val now = currentTimeMillis()
        removeExpiredCookiesLocked(now)
        cookiesByIdentity.values.any { cookie ->
            cookie.name == REFRESH_COOKIE_NAME && cookie.matches(url)
        }
    }

    fun clearAuthCookies(): Boolean = synchronized(lock) {
        cookiesByIdentity.clear()
        runCatching { persistence.clearCookies() }.getOrDefault(false)
    }

    override fun toString(): String = "PersistentRefreshCookieJar(cookies=<redacted>)"

    private fun restoreCookies(): LinkedHashMap<CookieIdentity, Cookie> {
        val persistedCookies = runCatching { persistence.readCookies() }
        if (persistedCookies.isFailure) {
            clearPersistenceBestEffort()
            return linkedMapOf()
        }
        val serialized = persistedCookies.getOrNull() ?: return linkedMapOf()
        val records = runCatching {
            gson.fromJson(serialized, Array<StoredCookieRecord?>::class.java)
        }.getOrNull()
        if (records == null) {
            clearPersistenceBestEffort()
            return linkedMapOf()
        }

        val now = currentTimeMillis()
        val restored = linkedMapOf<CookieIdentity, Cookie>()
        var discardedRecord = false
        records.forEach { record ->
            val cookie = record?.toCookie(now)
            if (cookie == null) {
                discardedRecord = true
            } else {
                restored[CookieIdentity(cookie.name, cookie.domain, cookie.path)] = cookie
            }
        }

        if (discardedRecord) {
            persistCookies(restored.values, now)
        }
        return restored
    }

    private fun removeExpiredCookiesLocked(now: Long) {
        val removed = cookiesByIdentity.entries.removeAll { (_, cookie) ->
            cookie.expiresAt <= now
        }
        if (removed) persistCurrentCookiesLocked(now)
    }

    private fun persistCurrentCookiesLocked(now: Long): Boolean {
        return persistCookies(cookiesByIdentity.values, now)
    }

    private fun persistCookies(cookies: Collection<Cookie>, now: Long): Boolean {
        val persistentRecords = cookies
            .filter { it.persistent && it.expiresAt > now }
            .map(StoredCookieRecord::fromCookie)

        return if (persistentRecords.isEmpty()) {
            runCatching { persistence.clearCookies() }.getOrDefault(false)
        } else {
            val serialized = runCatching { gson.toJson(persistentRecords) }.getOrNull()
            if (serialized == null) {
                clearPersistenceBestEffort()
                return false
            }
            val saved = runCatching {
                persistence.writeCookies(serialized)
            }.getOrDefault(false)
            if (!saved) clearPersistenceBestEffort()
            saved
        }
    }

    private fun clearPersistenceBestEffort() {
        runCatching { persistence.clearCookies() }
    }

    private data class CookieIdentity(
        val name: String,
        val domain: String,
        val path: String,
    )

    private class StoredCookieRecord(
        val name: String?,
        val encodedValue: String?,
        val domain: String?,
        val path: String?,
        val expiresAt: Long?,
        val secure: Boolean?,
        val httpOnly: Boolean?,
        val hostOnly: Boolean?,
        val persistent: Boolean?,
        val sameSite: String?,
    ) {
        fun toCookie(now: Long): Cookie? {
            if (name != REFRESH_COOKIE_NAME) return null
            val value = encodedValue?.let(::decodeSecret)
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val validDomain = domain?.takeIf { it.isNotBlank() } ?: return null
            val validPath = path?.takeIf { it.startsWith("/") } ?: return null
            val validExpiry = expiresAt?.takeIf { it > now } ?: return null
            if (persistent != true) return null

            return runCatching {
                Cookie.Builder()
                    .name(REFRESH_COOKIE_NAME)
                    .value(value)
                    .expiresAt(validExpiry)
                    .apply {
                        if (this@StoredCookieRecord.hostOnly == true) {
                            hostOnlyDomain(validDomain)
                        } else {
                            domain(validDomain)
                        }
                        path(validPath)
                        if (this@StoredCookieRecord.secure == true) secure()
                        if (this@StoredCookieRecord.httpOnly == true) httpOnly()
                        this@StoredCookieRecord.sameSite?.let { sameSite(it) }
                    }
                    .build()
            }.getOrNull()
        }

        override fun toString(): String {
            return "StoredCookieRecord(name=$name, encodedValue=<redacted>, domain=$domain, " +
                "path=$path, expiresAt=$expiresAt, secure=$secure, httpOnly=$httpOnly, " +
                "hostOnly=$hostOnly, persistent=$persistent, sameSite=$sameSite)"
        }

        companion object {
            fun fromCookie(cookie: Cookie): StoredCookieRecord {
                return StoredCookieRecord(
                    name = cookie.name,
                    encodedValue = encodeSecret(cookie.value),
                    domain = cookie.domain,
                    path = cookie.path,
                    expiresAt = cookie.expiresAt,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    hostOnly = cookie.hostOnly,
                    persistent = cookie.persistent,
                    sameSite = cookie.sameSite,
                )
            }
        }
    }

    companion object {
        const val REFRESH_COOKIE_NAME = "canmakan_refresh"

        private fun encodeSecret(value: String): String {
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        }

        private fun decodeSecret(value: String): String? = runCatching {
            val decoded = Base64.getUrlDecoder().decode(value)
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(decoded))
                .toString()
        }.getOrNull()
    }
}
