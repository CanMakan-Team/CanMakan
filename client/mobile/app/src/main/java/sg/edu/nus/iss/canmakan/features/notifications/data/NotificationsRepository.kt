package sg.edu.nus.iss.canmakan.features.notifications.data

import retrofit2.Response
import javax.inject.Inject

class NotificationsException(
    message: String,
    val statusCode: Int,
) : Exception(message)

class NotificationsRepository @Inject constructor(
    private val apiService: NotificationsApiService,
) {
    suspend fun listMine(): List<UserNotificationResponse> {
        val response = apiService.listMyNotifications()
        if (!response.isSuccessful) {
            throw NotificationsException(messageFromError(response), response.code())
        }
        return response.body().orEmpty()
    }

    suspend fun markAllRead() {
        val response = apiService.markNotificationsRead()
        if (!response.isSuccessful) {
            throw NotificationsException(messageFromError(response), response.code())
        }
    }

    suspend fun delete(notificationId: Long) {
        val response = apiService.deleteNotification(notificationId)
        if (!response.isSuccessful) {
            throw NotificationsException(messageFromError(response), response.code())
        }
    }

    private fun messageFromError(response: Response<*>): String {
        val raw = response.errorBody()?.string().orEmpty()
        if (raw.isBlank()) {
            return "Could not update notifications."
        }
        val match = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(raw)
        val extracted = match?.groupValues?.getOrNull(1).orEmpty()
        return extracted.ifBlank { "Could not update notifications." }
    }
}
