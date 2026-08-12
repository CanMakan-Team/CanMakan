package sg.edu.nus.iss.canmakan.features.product.history.data

import jakarta.inject.Inject
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ScanHistoryEntry

class ServerScanHistoryRepository @Inject constructor (
    private val scanHistoryApiService: ScanHistoryApiService
): ScanHistoryRepository {
    override suspend fun getScanHistoryForProfile(profileId: Long): List<ScanHistoryEntry> {
        require(profileId > 0) { "Scan history requires a positive profile id." }
        return scanHistoryApiService.getScanHistoryForProfile(profileId)
    }

//    override suspend fun getProductFromBarcode(barcode: String): Product {
//        return scanHistoryApiService.getProductByBarcode(barcode)
//    }
}
