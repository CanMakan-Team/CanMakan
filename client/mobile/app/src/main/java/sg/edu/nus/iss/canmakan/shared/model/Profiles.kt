package sg.edu.nus.iss.canmakan.shared.model

// A person whose dietary needs are tracked in the app.
data class DietaryProfile(
    val id: Long,
    val name: String,
    val role: String,
    val initials: String
)
