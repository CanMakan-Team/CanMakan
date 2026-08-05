package sg.edu.nus.iss.canmakan.features.product.history.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanHistoryRepoModule {
    @Binds
    @Singleton
    abstract fun bindScanHistoryRepository(
        repository: ServerScanHistoryRepository
    ): ScanHistoryRepository
}