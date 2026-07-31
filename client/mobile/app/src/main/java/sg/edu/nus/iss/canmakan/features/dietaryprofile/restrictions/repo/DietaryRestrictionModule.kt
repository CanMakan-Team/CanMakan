package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.repo

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.repo.local.SampleDietaryRestrictionRepository
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