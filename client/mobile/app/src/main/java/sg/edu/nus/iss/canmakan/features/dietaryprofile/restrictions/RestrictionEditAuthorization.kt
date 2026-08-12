package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeResponse

/**
 * D3: PRIMARY_ADMIN may edit restrictions for any profile in their family circle.
 * Non-admins may only edit their own linked profile.
 */
object RestrictionEditAuthorization {
    const val ROLE_PRIMARY_ADMIN = "PRIMARY_ADMIN"

    const val EDIT_DIETARY_PROFILE_LABEL = "Edit dietary profile"
    const val VIEW_DIETARY_PROFILE_LABEL = "View dietary profile"

    const val READ_ONLY_HINT =
        "Only the family admin can edit another member's dietary restrictions."

    /**
     * @param hasFamily false when GET /families/me returned 404 (personal profile only).
     */
    fun mayEditRestrictions(
        profileId: Long,
        hasFamily: Boolean,
        me: FamilyMeResponse?,
    ): Boolean {
        return mayEditRestrictions(
            profileId = profileId,
            hasFamily = hasFamily,
            selfProfileId = me?.selfProfileId,
            memberRole = me?.memberRole,
        )
    }

    fun mayEditRestrictions(
        profileId: Long,
        hasFamily: Boolean,
        selfProfileId: Long?,
        memberRole: String?,
    ): Boolean {
        if (!hasFamily || selfProfileId == null) {
            return true
        }
        if (profileId == selfProfileId) {
            return true
        }
        return memberRole == ROLE_PRIMARY_ADMIN
    }

    fun dietaryProfileButtonLabel(allowEdit: Boolean): String {
        return if (allowEdit) EDIT_DIETARY_PROFILE_LABEL else VIEW_DIETARY_PROFILE_LABEL
    }
}
