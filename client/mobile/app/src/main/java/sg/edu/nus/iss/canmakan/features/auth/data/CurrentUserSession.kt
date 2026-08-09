package sg.edu.nus.iss.canmakan.features.auth.data

/**
 * Caller identity for temporary `X-User-Id` until UC19 JWT.
 */
interface CurrentUserSession {
    val userId: Long?
    val selfProfileId: Long?

    fun save(userId: Long, selfProfileId: Long)

    fun clear()
}
