package sg.edu.nus.iss.canmakan.features.family.data

import com.google.gson.annotations.SerializedName

data class FamilyProfileResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("profileName") val profileName: String,
    @SerializedName("familyId") val familyId: Long,
    @SerializedName("relationship") val relationship: String,
    @SerializedName("initials") val initials: String,
    @SerializedName("isPrimary") val isPrimary: Boolean
)
