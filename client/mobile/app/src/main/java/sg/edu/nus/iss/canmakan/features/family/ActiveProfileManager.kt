package sg.edu.nus.iss.canmakan.features.family

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveProfileManager @Inject constructor() {
    private val _currentProfileId = MutableStateFlow(UNSET_PROFILE_ID)
    val currentProfileId: StateFlow<Long> = _currentProfileId

    fun switchProfile(profileId: Long) {
        _currentProfileId.value = profileId
    }

    companion object {
        /** No profile resolved yet; replaced after register or GET active-profile. */
        const val UNSET_PROFILE_ID = 0L
    }
}
