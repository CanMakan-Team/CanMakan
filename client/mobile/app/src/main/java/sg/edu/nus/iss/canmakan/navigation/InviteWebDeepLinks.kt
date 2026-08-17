package sg.edu.nus.iss.canmakan.navigation

import java.net.URI
import sg.edu.nus.iss.canmakan.BuildConfig

/**
 * HTTPS invite hosts come from {@code CANMAKAN_INVITES_PUBLIC_BASE_URL}
 * (local.properties, Gradle property, or env; same name as backend).
 * Custom scheme {@code canmakan://invite/{token}} stays app-owned.
 * Firebase App Distribution install links are backend/web shared
 * ({@code FIREBASE_APP_DISTRIBUTION_URL}) and are not App Link hosts.
 */
object InviteWebDeepLinks {

    fun uriPatterns(baseUrlsCsv: String = BuildConfig.CANMAKAN_INVITES_PUBLIC_BASE_URL): List<String> {
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
    fun familyPortalMembersUrl(baseUrlsCsv: String = BuildConfig.CANMAKAN_INVITES_PUBLIC_BASE_URL): String? {
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
