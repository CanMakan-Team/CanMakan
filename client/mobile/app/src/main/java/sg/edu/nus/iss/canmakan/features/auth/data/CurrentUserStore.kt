package sg.edu.nus.iss.canmakan.features.auth.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences-backed [CurrentUserSession].
 */
@Singleton
class CurrentUserStore @Inject constructor(
    @ApplicationContext context: Context,
) : CurrentUserSession {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val userId: Long?
        get() = preferences.getLong(KEY_USER_ID, ABSENT).takeUnless { it == ABSENT }

    override val selfProfileId: Long?
        get() = preferences.getLong(KEY_SELF_PROFILE_ID, ABSENT).takeUnless { it == ABSENT }

    override fun save(userId: Long, selfProfileId: Long) {
        preferences.edit()
            .putLong(KEY_USER_ID, userId)
            .putLong(KEY_SELF_PROFILE_ID, selfProfileId)
            .apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "canmakan.current_user"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SELF_PROFILE_ID = "self_profile_id"
        private const val ABSENT = -1L
    }
}
