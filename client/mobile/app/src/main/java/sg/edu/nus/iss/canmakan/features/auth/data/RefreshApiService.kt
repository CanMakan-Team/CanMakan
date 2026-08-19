package sg.edu.nus.iss.canmakan.features.auth.data

import retrofit2.Call
import retrofit2.http.Headers
import retrofit2.http.POST

/** Non-suspending endpoints used only by the dedicated, non-recursive auth client. */
interface RefreshApiService {
    @Headers("X-CanMakan-Session-Request: 1")
    @POST("auth/refresh")
    fun refresh(): Call<AuthResponse>

    @Headers("X-CanMakan-Session-Request: 1")
    @POST("auth/logout")
    fun logout(): Call<Unit>
}
