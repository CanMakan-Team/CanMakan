package sg.edu.nus.iss.canmakan.features.auth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import sg.edu.nus.iss.canmakan.features.auth.data.CurrentUserSession
import sg.edu.nus.iss.canmakan.features.auth.data.CurrentUserStore
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationRepository
import sg.edu.nus.iss.canmakan.features.auth.data.ServerRegistrationRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RegistrationModule {

    @Binds
    @Singleton
    abstract fun bindRegistrationRepository(
        repository: ServerRegistrationRepository,
    ): RegistrationRepository

    @Binds
    @Singleton
    abstract fun bindCurrentUserSession(
        store: CurrentUserStore,
    ): CurrentUserSession
}
