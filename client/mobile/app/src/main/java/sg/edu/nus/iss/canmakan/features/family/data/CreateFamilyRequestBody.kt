package sg.edu.nus.iss.canmakan.features.family.data

import com.google.gson.annotations.SerializedName

data class CreateFamilyRequestBody(
    @SerializedName("familyName") val familyName: String,
)
