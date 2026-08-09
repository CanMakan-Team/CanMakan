package sg.edu.nus.iss.canmakan.features.auth.data

import retrofit2.Call
import retrofit2.http.POST

/** Non-suspending endpoint used only by the dedicated, non-recursive refresh client. */
interface RefreshApiService {
    @POST("auth/refresh")
    fun refresh(): Call<AuthResponse>
}
