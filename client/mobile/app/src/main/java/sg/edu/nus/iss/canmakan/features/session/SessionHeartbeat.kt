package sg.edu.nus.iss.canmakan.features.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.session.data.SessionApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends a session heartbeat every [heartbeatIntervalMs] while the app is in the foreground, so the
 * backend can measure real "time spent in the app" (UC15 engagement).
 *
 * This class holds only the foreground/heartbeat logic and has no Android dependencies, so it can be
 * unit tested over virtual time. The Android glue that detects foreground - counting started
 * activities and calling [activityStarted]/[activityStopped] - lives in the Application, which forwards
 * those two edges here. The loop starts on the background -> foreground edge and stops on the
 * foreground -> background edge. A dropped heartbeat is ignored; the backend session timeout (a few
 * intervals) tolerates it.
 *
 * The Hilt-injected constructor runs the loop on an IO scope at [DEFAULT_HEARTBEAT_INTERVAL_MS]. The
 * second constructor exists only for tests, which pass a test scope and a short interval so the loop
 * can be driven deterministically over virtual time.
 *
 * @author XieHuayuan
 */
@Singleton
class SessionHeartbeat internal constructor(
    private val sessionApiService: SessionApiService,
    private val scope: CoroutineScope,
    private val heartbeatIntervalMs: Long,
) {

    @Inject
    constructor(sessionApiService: SessionApiService) : this(
        sessionApiService,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        DEFAULT_HEARTBEAT_INTERVAL_MS,
    )

    private var startedActivities = 0
    private var heartbeatJob: Job? = null

    /** Records that an activity started; the loop starts only on the 0 -> 1 (foreground) edge. */
    fun activityStarted() {
        if (startedActivities++ == 0) {
            startHeartbeat()
        }
    }

    /** Records that an activity stopped; the loop stops only on the 1 -> 0 (background) edge. */
    fun activityStopped() {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) {
            stopHeartbeat()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    sessionApiService.heartbeat()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    // A dropped heartbeat is tolerated by the backend session timeout.
                }
                delay(heartbeatIntervalMs)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    companion object {
        const val DEFAULT_HEARTBEAT_INTERVAL_MS = 30_000L
    }
}
