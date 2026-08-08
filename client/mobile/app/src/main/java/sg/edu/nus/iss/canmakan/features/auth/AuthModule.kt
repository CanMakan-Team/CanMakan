package sg.edu.nus.iss.canmakan.features.auth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRepository
import sg.edu.nus.iss.canmakan.features.auth.data.ServerAuthRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        repository: ServerAuthRepository,
    ): AuthRepository
}
