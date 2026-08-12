package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeResponse

/**
 * D3: PRIMARY_ADMIN may edit restrictions for any profile in their family circle.
 * Non-admins may only edit their own linked profile.
 */
object RestrictionEditAuthorization {
    const val ROLE_PRIMARY_ADMIN = "PRIMARY_ADMIN"

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
        if (!hasFamily || me == null) {
            return true
        }
        if (profileId == me.selfProfileId) {
            return true
        }
        return me.memberRole == ROLE_PRIMARY_ADMIN
    }
}
