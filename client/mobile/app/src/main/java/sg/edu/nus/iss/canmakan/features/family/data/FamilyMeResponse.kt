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
