package sg.edu.nus.iss.canmakan.dietaryrestriction

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class DietaryRestriction(
    val id: Long,
    val code: String,
    val displayName: String,
    val category: String,
    val description: String?
)

data class ProfileRestriction(
    val dietaryProfileId: Long,
    val dietaryRestrictionId: Long,
    val severityLevel: String = "STRICT_AVOID"
)

@HiltViewModel
class DietaryRestrictionViewModel @Inject constructor(
    private val dietaryRestrictionRepo: DietaryRestrictionRepository,
) {


}