package sg.edu.nus.iss.canmakan.features.dietaryprofile.model

// One selectable option in the Edit Dietary Requirements sheet.
data class DietaryOption(
    val label: String,
    val isSelected: Boolean = false
)
