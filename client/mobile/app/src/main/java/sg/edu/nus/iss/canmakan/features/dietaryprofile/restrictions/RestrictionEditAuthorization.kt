package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import sg.edu.nus.iss.canmakan.features.family.data.FamilyMemberRosterItem
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeResponse

/**
 * D3: the actor may edit restrictions for their self-linked profile or for
 * unlinked dependants when they are PRIMARY_ADMIN. Another adult's linked
 * profile stays read-only.
 */
object RestrictionEditAuthorization {
    const val SOURCE_DEPENDANT = "DEPENDANT_PROFILE"
    const val ROLE_PRIMARY_ADMIN = "PRIMARY_ADMIN"

    const val READ_ONLY_HINT =
        "Restrictions for another adult's linked profile can only be edited by that member."

    /**
     * @param hasFamily false when GET /families/me returned 404 (personal profile only).
     */
    fun mayEditRestrictions(
        profileId: Long,
        hasFamily: Boolean,
        me: FamilyMeResponse?,
        members: List<FamilyMemberRosterItem>,
    ): Boolean {
        if (!hasFamily || me == null) {
            return true
        }
        if (profileId == me.selfProfileId) {
            return true
        }
        val target = members.firstOrNull { it.profileId == profileId } ?: return false
        if (target.source == SOURCE_DEPENDANT) {
            return me.memberRole == ROLE_PRIMARY_ADMIN
        }
        val selfLinkedUserId = members
            .firstOrNull { it.profileId == me.selfProfileId }
            ?.linkedUserId
        return target.linkedUserId != null &&
            selfLinkedUserId != null &&
            target.linkedUserId == selfLinkedUserId
    }
}
