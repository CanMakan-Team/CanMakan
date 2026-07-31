package sg.edu.nus.iss.canmakan.features.userprofile.model

// One selectable option in the Edit Dietary Requirements sheet.
data class DietaryOption(
    val label: String,
    val isSelected: Boolean = false
)
