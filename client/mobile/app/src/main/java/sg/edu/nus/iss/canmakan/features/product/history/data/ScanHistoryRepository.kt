package sg.edu.nus.iss.canmakan.features.product.history.data

import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ScanHistoryEntry

interface ScanHistoryRepository {

    suspend fun getScanHistoryForProfile(profileId: Long): List<ScanHistoryEntry>

    suspend fun getProductFromBarcode(barcode: String): Product

}