package sg.edu.nus.iss.canmakan.shared.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import sg.edu.nus.iss.canmakan.R
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** [SystemNotifier] backed by [NotificationManagerCompat]. */
@Singleton
class AndroidSystemNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemNotifier {
    private val notificationManagerCompat = NotificationManagerCompat.from(context)

    init {
        createChannelIfNeeded()
    }

    override fun notify(id: Int, title: String, body: String, notificationsEnabled: Boolean) {
        if (!notificationsEnabled) return
        if (!hasPostPermission()) {
            Timber.i("Skipped system notification: POST_NOTIFICATIONS not granted.")
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManagerCompat.notify(id, notification)
    }

    private fun hasPostPermission(): Boolean {
        // The permission only exists from API 33 onward; earlier versions show
        // notifications for any app that posts them.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    // NotificationChannel is required from API 26 onward, which matches this app's minSdk,
    // so no version check is needed here. Re-creating an existing channel is a no-op.
    private fun createChannelIfNeeded() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        notificationManagerCompat.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "canmakan_alerts"
        private const val CHANNEL_NAME = "CanMakan Alerts"
        private const val CHANNEL_DESCRIPTION =
            "Family invites and other account updates from CanMakan."
    }
}
