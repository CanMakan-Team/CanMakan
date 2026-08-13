package sg.edu.nus.iss.canmakan.features.family

import java.util.Locale
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.RestrictionEditAuthorization

/**
 * Labels shown next to a dietary profile.
 *
 * [relationship] on the server is SELF for the family admin's linked profile.
 * Invitees store the relationship the admin chose (Spouse, Child, …).
 * "Self" in the UI means the currently signed-in user's profile, not that column.
 * Child / Spouse / etc. are from the admin's point of view, so only the family
 * admin sees those captions.
 */
object ProfileRelationshipDisplay {

    data class Tags(
        val showAdminTag: Boolean,
        val caption: String?,
    )

    fun tags(
        profileId: Long,
        relationship: String?,
        isFamilyAdminProfile: Boolean,
        viewerSelfProfileId: Long?,
        viewerMemberRole: String?,
    ): Tags {
        val isOwnProfile = viewerSelfProfileId != null && profileId == viewerSelfProfileId
        val viewerIsFamilyAdmin =
            viewerMemberRole == RestrictionEditAuthorization.ROLE_PRIMARY_ADMIN
        val caption = when {
            isOwnProfile -> "Self"
            viewerIsFamilyAdmin -> formatRelationshipCaption(relationship)
            else -> null
        }
        return Tags(
            showAdminTag = isFamilyAdminProfile,
            caption = caption,
        )
    }

    fun formatRelationshipCaption(relationship: String?): String? {
        val trimmed = relationship?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("SELF", ignoreCase = true)) {
            return null
        }
        if (trimmed.equals("DEPENDENT", ignoreCase = true)
            || trimmed.equals("DEPENDANT", ignoreCase = true)
        ) {
            return "Dependant"
        }
        return trimmed.lowercase(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun sheetRoleLine(tags: Tags): String {
        return listOfNotNull(
            if (tags.showAdminTag) "Admin" else null,
            tags.caption,
        ).joinToString(" · ")
    }
}
