package sg.edu.nus.iss.canmakan.features.family.data

/** HTTP failure from family, invitation, or user-preference endpoints. */
class FamilyApiException(
    message: String,
    val statusCode: Int,
) : Exception(message)
