package sg.edu.nus.iss.canmakan.features.notifications.data

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.Response

class NotificationsRepositoryTest {

    @Test
    fun listMineReturnsBodyOrEmpty() = runTest {
        val item = sampleNotification()
        assertEquals(
            listOf(item),
            NotificationsRepository(FakeApi(listResponse = Response.success(listOf(item)))).listMine(),
        )
        assertTrue(NotificationsRepository(FakeApi(listResponse = Response.success(null))).listMine().isEmpty())
    }

    @Test
    fun inviteRequestCanAcceptWhenTokenPresent() {
        val actionable = UserNotificationResponse(
            id = 2L,
            type = "FAMILY_INVITE_REQUEST",
            title = "Join?",
            body = null,
            actionToken = " tok ",
            expired = false,
            read = false,
            updatedAt = null,
        )
        assertTrue(actionable.canAcceptOrDecline)
        assertTrue(
            !UserNotificationResponse(
                id = 3L,
                type = "FAMILY_INVITE_REQUEST",
                title = "Join?",
                body = null,
                actionToken = "  ",
                expired = false,
                read = false,
                updatedAt = null,
            ).canAcceptOrDecline,
        )
    }

    @Test
    fun unsuccessfulListSurfacesJsonMessage() = runTest {
        val error = runCatching {
            NotificationsRepository(FakeApi(listResponse = error(503, """{"message":"inbox offline"}"""))).listMine()
        }.exceptionOrNull()

        val typed = assertInstanceOf(NotificationsException::class.java, error)
        assertEquals("inbox offline", typed.message)
        assertEquals(503, typed.statusCode)
    }

    @Test
    fun unsuccessfulMarkAndDeleteUseMessageOrFallback() = runTest {
        val repository = NotificationsRepository(
            FakeApi(
                markReadResponse = error(400, """{"message":"already read"}"""),
                deleteResponse = error(500, "{}"),
            ),
        )

        val markError = assertInstanceOf(
            NotificationsException::class.java,
            runCatching { repository.markAllRead() }.exceptionOrNull(),
        )
        assertEquals("already read", markError.message)

        val deleteError = assertInstanceOf(
            NotificationsException::class.java,
            runCatching { repository.delete(9L) }.exceptionOrNull(),
        )
        assertEquals("Could not update notifications.", deleteError.message)
        assertEquals(500, deleteError.statusCode)
    }

    @Test
    fun blankErrorBodyUsesFallback() = runTest {
        val error = runCatching {
            NotificationsRepository(FakeApi(listResponse = error(502, "   "))).listMine()
        }.exceptionOrNull()
        assertEquals("Could not update notifications.", error?.message)
    }

    @Test
    fun successfulMutationsDoNotThrow() = runTest {
        val repository = NotificationsRepository(
            FakeApi(
                markReadResponse = Response.success(Unit),
                deleteResponse = Response.success(Unit),
            ),
        )
        repository.markAllRead()
        repository.delete(4L)
    }

    private fun sampleNotification(): UserNotificationResponse {
        return UserNotificationResponse(
            id = 1L,
            type = "FAMILY_INVITE_UPDATE",
            title = "Invite sent",
            body = "Wong Family",
            actionToken = null,
            expired = false,
            read = false,
            updatedAt = "2026-08-14T00:00:00Z",
        )
    }

    private fun <T> error(status: Int, body: String): Response<T> {
        return Response.error(status, body.toResponseBody("application/json".toMediaType()))
    }

    private class FakeApi(
        private val listResponse: Response<List<UserNotificationResponse>> = Response.success(emptyList()),
        private val markReadResponse: Response<Unit> = Response.success(Unit),
        private val deleteResponse: Response<Unit> = Response.success(Unit),
    ) : NotificationsApiService {
        override suspend fun listMyNotifications(): Response<List<UserNotificationResponse>> = listResponse

        override suspend fun markNotificationsRead(): Response<Unit> = markReadResponse

        override suspend fun deleteNotification(notificationId: Long): Response<Unit> = deleteResponse
    }
}
