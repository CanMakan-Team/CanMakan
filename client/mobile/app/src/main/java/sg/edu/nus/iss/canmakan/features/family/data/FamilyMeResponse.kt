package sg.edu.nus.iss.canmakan.features.family.data

import com.google.gson.annotations.SerializedName

/** Domain + wire model for GET /api/families/me (UC8). */
data class FamilyMeResponse(
    @SerializedName("familyId") val familyId: Long,
    @SerializedName("familyName") val familyName: String,
    @SerializedName("memberRole") val memberRole: String,
    @SerializedName("selfProfileId") val selfProfileId: Long,
    @SerializedName("createdByUserId") val createdByUserId: Long,
)

/** (UC6) Domain + wire model for GET /api/families/me/restriction-summary. */
data class FamilyRestrictionSumRes (
    @SerializedName("familyMembers") val familyMembers: List<FamilyMeRestrictionSum>
)

data class FamilyMeRestrictionSum (
    @SerializedName("userId") val userId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("isActive") val isActive: Boolean,
    @SerializedName("restrictions") val restrictions: List<FamilyMeRestrictionDetail>
)

data class FamilyMeRestrictionDetail (
    @SerializedName("code") val code: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("severity") val severity: String,
)
