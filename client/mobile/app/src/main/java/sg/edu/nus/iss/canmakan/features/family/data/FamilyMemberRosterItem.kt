package sg.edu.nus.iss.canmakan.features.family.data

import com.google.gson.annotations.SerializedName

/**
 * Wire model for one row of GET /api/families/me/members (UC12 roster).
 * Aligns with backend [FamilyMemberRosterDto].
 */
data class FamilyMemberRosterItem(
    @SerializedName("memberId") val memberId: Long,
    @SerializedName("profileId") val profileId: Long,
    @SerializedName("linkedUserId") val linkedUserId: Long?,
    @SerializedName("profileName") val profileName: String,
    @SerializedName("relationship") val relationship: String?,
    @SerializedName("commonRequirements") val commonRequirements: List<String>? = null,
    @SerializedName("restrictions") val restrictions: List<String>? = null,
    @SerializedName("source") val source: String,
    @SerializedName("maskedEmail") val maskedEmail: String? = null,
    @SerializedName("memberRole") val memberRole: String? = null,
    @SerializedName("profileActive") val profileActive: Boolean = true,
)
