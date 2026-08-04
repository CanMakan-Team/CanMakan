package sg.edu.nus.iss.canmakan.features.product.history.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.product.model.ScanHistoryEntry

interface ScanHistoryApiService {
    @GET("restrictions")
    suspend fun getScanHistoryForProfile(): List<ScanHistoryEntry>



}