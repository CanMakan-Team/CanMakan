package sg.edu.nus.iss.canmakan

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree

/**
 * Application that sets up Timber in the DEBUG BuildConfig.
 * Read Timber's documentation for production setups.
 */
@HiltAndroidApp
class CanMakanApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (sg.edu.nus.iss.canmakan.BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }
}
