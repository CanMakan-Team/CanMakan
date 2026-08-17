package sg.edu.nus.iss.canmakan.features.session.data

import retrofit2.Response
import retrofit2.http.POST

/**
 * Sends in-app session heartbeats to the backend (UC15 engagement tracking).
 *
 * The base URL already includes the "/api" prefix (see NetworkModule), so the path here is relative,
 * matching the other API interfaces. The endpoint is authenticated; the user is taken from the token.
 */
interface SessionApiService {

    @POST("sessions/heartbeat")
    suspend fun heartbeat(): Response<Unit>
}
