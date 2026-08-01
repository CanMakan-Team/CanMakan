package sg.edu.nus.iss.canmakan.shared.model

// A person whose dietary needs are tracked in the app.
data class DietaryProfile(
    val id: Long,
    val familyId: Long,
    val profileName: String,
    val relationship: String,
    val initials: String,
    val isPrimary: Boolean
)
