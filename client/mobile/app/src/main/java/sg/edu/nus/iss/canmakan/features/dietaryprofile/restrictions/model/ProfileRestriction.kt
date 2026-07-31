package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model

data class ProfileRestriction(
    val dietaryProfileId: Long,
    val dietaryRestrictionId: Long,
    val severityLevel: String = "STRICT_AVOID"
)
