package sg.edu.nus.iss.canmakan.features.userprofile

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DietaryRestrictionModule {
    @Binds
    @Singleton
    abstract fun bindDietaryRestrictionRepository(
        repository: SampleDietaryRestrictionRepository,
    ): DietaryRestrictionRepository
}
