package sg.edu.nus.iss.canmakan.features.dietaryprofile.setup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.SelfProfileRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.ServerSelfProfileRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class SelfProfileModule {
    @Binds
    @Singleton
    abstract fun bindSelfProfileRepository(
        repository: ServerSelfProfileRepository,
    ): SelfProfileRepository
}
