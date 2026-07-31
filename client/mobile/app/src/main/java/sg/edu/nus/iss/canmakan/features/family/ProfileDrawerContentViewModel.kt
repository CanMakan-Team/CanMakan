package sg.edu.nus.iss.canmakan.features.family

import androidx.lifecycle.ViewModel
import javax.inject.Inject

class ProfileDrawerContentViewModel @Inject constructor(
    private val activeProfileManager: ActiveProfileManager): ViewModel() {
    fun selectProfile(profileId: Long) {
        activeProfileManager.switchProfile(profileId)
    }
}