package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

// Hilt binding for the dietary restriction repository so the ViewModel receives a concrete implementation.
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
// import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.SampleDietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.ServerDietaryRestrictionRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DietaryRestrictionModule {
    @Binds
    @Singleton
    abstract fun bindDietaryRestrictionRepository(
        // repository: SampleDietaryRestrictionRepository,
        repository: ServerDietaryRestrictionRepository
    ): DietaryRestrictionRepository
}
