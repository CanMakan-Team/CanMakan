package sg.edu.nus.iss.canmakan.features.notifications.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class UserNotificationResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String?,
    @SerializedName("actionToken") val actionToken: String?,
    @SerializedName("expired") val expired: Boolean,
    @SerializedName("read") val read: Boolean,
    @SerializedName("updatedAt") val updatedAt: String?,
) {
    val canAcceptOrDecline: Boolean
        get() = type.equals("FAMILY_INVITE_REQUEST", ignoreCase = true)
            && !actionToken.isNullOrBlank()
}

interface NotificationsApiService {
    @GET("notifications/me")
    suspend fun listMyNotifications(): Response<List<UserNotificationResponse>>

    @POST("notifications/me/read")
    suspend fun markNotificationsRead(): Response<Unit>

    @DELETE("notifications/{id}")
    suspend fun deleteNotification(
        @Path("id") notificationId: Long,
    ): Response<Unit>
}
