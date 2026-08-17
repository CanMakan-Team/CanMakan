package sg.edu.nus.iss.canmakan.navigation

import java.net.URI
import sg.edu.nus.iss.canmakan.BuildConfig

/**
 * HTTPS invite hosts come from {@code WEB_INVITE_BASE_URLS}
 * (local.properties, Gradle property, or env). Custom scheme stays app-owned.
 */
object InviteWebDeepLinks {

    fun uriPatterns(baseUrlsCsv: String = BuildConfig.WEB_INVITE_BASE_URLS): List<String> {
        val webPatterns = baseUrlsCsv.split(",")
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotEmpty() }
            .map { "$it/invite/{token}" }
        return webPatterns + "canmakan://invite/{token}"
    }

    /**
     * Public User Portal home (`/family` resolver). Local emulator hosts are skipped so
     * a device does not try to open localhost.
     */
    fun familyPortalMembersUrl(baseUrlsCsv: String = BuildConfig.WEB_INVITE_BASE_URLS): String? {
        val origin = baseUrlsCsv.split(",")
            .map { it.trim().trimEnd('/') }
            .firstOrNull { raw -> isPublicHttpsOrigin(raw) }
            ?: return null
        return "$origin/family"
    }

    private fun isPublicHttpsOrigin(raw: String): Boolean {
        if (raw.isEmpty()) {
            return false
        }
        return try {
            val uri = URI(raw)
            val host = uri.host.orEmpty()
            uri.scheme.equals("https", ignoreCase = true) &&
                host.isNotBlank() &&
                !host.equals("localhost", ignoreCase = true) &&
                host != "127.0.0.1"
        } catch (_: Exception) {
            false
        }
    }
}
