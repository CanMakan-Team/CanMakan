package sg.edu.nus.iss.canmakan.features.product.scan.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanRepoModule {
    @Binds
    @Singleton
    abstract fun bindScanRepository(
        repository: ServerScanRepository,
    ): ScanRepository
}
