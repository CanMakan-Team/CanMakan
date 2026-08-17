package sg.edu.nus.iss.canmakan.features.session

import java.io.IOException
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response
import sg.edu.nus.iss.canmakan.features.session.data.SessionApiService

/**
 * Unit tests for the foreground heartbeat loop (UC15 engagement). The loop is driven over virtual
 * time by handing [SessionHeartbeat] the test's [backgroundScope] and a short interval, so no real
 * time passes.
 *
 * @author XieHuayuan
 */
@DisplayName("UC15: SessionHeartbeat foreground loop")
class SessionHeartbeatTest {

    private companion object {
        const val INTERVAL_MS = 1_000L
    }

    @Test
    @DisplayName("fires immediately on foreground entry and again each interval")
    fun firesImmediatelyAndOnEachInterval() = runTest {
        val api = FakeSessionApiService()
        val heartbeat = SessionHeartbeat(api, backgroundScope, INTERVAL_MS)

        heartbeat.activityStarted()
        runCurrent()
        assertEquals(1, api.callCount, "should send one heartbeat as soon as the app is foregrounded")

        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        assertEquals(2, api.callCount)

        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        assertEquals(3, api.callCount)

        heartbeat.activityStopped()
    }

    @Test
    @DisplayName("stops sending heartbeats once the app goes to the background")
    fun stopsWhenBackgrounded() = runTest {
        val api = FakeSessionApiService()
        val heartbeat = SessionHeartbeat(api, backgroundScope, INTERVAL_MS)

        heartbeat.activityStarted()
        runCurrent()
        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        assertEquals(2, api.callCount)

        heartbeat.activityStopped()
        advanceTimeBy(INTERVAL_MS * 5)
        runCurrent()

        assertEquals(2, api.callCount, "no heartbeats should be sent while backgrounded")
    }

    @Test
    @DisplayName("keeps running across an activity change and only stops when the last activity stops")
    fun keepsRunningWhileAnyActivityIsStarted() = runTest {
        val api = FakeSessionApiService()
        val heartbeat = SessionHeartbeat(api, backgroundScope, INTERVAL_MS)

        heartbeat.activityStarted()
        runCurrent()

        // A second activity starts while the first is still started (e.g. navigating), then one stops.
        // The app is still in the foreground, so the loop must not restart or stop.
        heartbeat.activityStarted()
        heartbeat.activityStopped()

        val before = api.callCount
        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        assertTrue(api.callCount > before, "still foreground, so heartbeats should continue")

        // The last started activity stops -> background -> loop stops.
        heartbeat.activityStopped()
        val afterBackground = api.callCount
        advanceTimeBy(INTERVAL_MS * 3)
        runCurrent()
        assertEquals(afterBackground, api.callCount)
    }

    @Test
    @DisplayName("a failed heartbeat is swallowed and the loop keeps sending")
    fun failedHeartbeatDoesNotStopTheLoop() = runTest {
        val api = FakeSessionApiService(error = IOException("network down"))
        val heartbeat = SessionHeartbeat(api, backgroundScope, INTERVAL_MS)

        heartbeat.activityStarted()
        runCurrent()
        assertEquals(1, api.callCount)

        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        assertEquals(2, api.callCount, "a dropped heartbeat must not cancel the loop")

        heartbeat.activityStopped()
    }

    @Test
    @DisplayName("the injected constructor builds a heartbeat using the default interval")
    fun injectedConstructorUsesDefaults() {
        val heartbeat = SessionHeartbeat(FakeSessionApiService())

        assertNotNull(heartbeat)
    }

    /** Records how many heartbeats were sent; optionally fails every call to simulate a network error. */
    private class FakeSessionApiService(
        private val error: Throwable? = null,
    ) : SessionApiService {

        var callCount = 0
            private set

        override suspend fun heartbeat(): Response<Unit> {
            callCount++
            error?.let { throw it }
            return Response.success(Unit)
        }
    }
}
