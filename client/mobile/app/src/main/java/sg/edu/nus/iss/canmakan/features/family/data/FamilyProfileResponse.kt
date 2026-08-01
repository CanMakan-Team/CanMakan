package sg.edu.nus.iss.canmakan.features.family.data

import com.google.gson.annotations.SerializedName

data class FamilyProfileResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("role") val role: String,
    @SerializedName("initials") val initials: String
)
