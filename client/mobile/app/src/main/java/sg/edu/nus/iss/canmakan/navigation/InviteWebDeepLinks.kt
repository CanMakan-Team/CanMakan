package sg.edu.nus.iss.canmakan.navigation

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
}
