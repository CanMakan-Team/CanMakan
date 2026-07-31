package sg.edu.nus.iss.canmakan.features.dietaryprofile.model

import android.R.attr.category

// One selectable option in the Edit Dietary Requirements sheet.
data class DietaryRestriction(
    val id: Long,
    val code: String,
    val displayName: String,
    val category: String,
    val description: String
)
