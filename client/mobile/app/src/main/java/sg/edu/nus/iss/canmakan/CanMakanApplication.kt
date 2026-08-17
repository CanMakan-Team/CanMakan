package sg.edu.nus.iss.canmakan

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import sg.edu.nus.iss.canmakan.features.session.SessionHeartbeat
import timber.log.Timber
import timber.log.Timber.DebugTree

/**
 * Application that sets up Timber in the DEBUG BuildConfig and drives the in-app session heartbeat
 * (UC15 engagement tracking). Foreground is detected here by counting started activities; each edge
 * is forwarded to [SessionHeartbeat], which owns the heartbeat loop.
 */
@HiltAndroidApp
class CanMakanApplication : Application(), Application.ActivityLifecycleCallbacks {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HeartbeatEntryPoint {
        fun sessionHeartbeat(): SessionHeartbeat
    }

    private val sessionHeartbeat: SessionHeartbeat by lazy {
        EntryPointAccessors
            .fromApplication(this, HeartbeatEntryPoint::class.java)
            .sessionHeartbeat()
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        sessionHeartbeat.activityStarted()
    }

    override fun onActivityStopped(activity: Activity) {
        sessionHeartbeat.activityStopped()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
