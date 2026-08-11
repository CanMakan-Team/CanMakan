package sg.edu.nus.iss.canmakan.features.auth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRepository
import sg.edu.nus.iss.canmakan.features.auth.data.ServerAuthRepository
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionPersistence
import sg.edu.nus.iss.canmakan.features.auth.session.AuthLogoutAction
import sg.edu.nus.iss.canmakan.features.auth.session.EncryptedAuthPreferences
import sg.edu.nus.iss.canmakan.features.auth.session.RefreshCookiePersistence
import sg.edu.nus.iss.canmakan.features.auth.session.SerializedAuthLogoutAction

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        repository: ServerAuthRepository,
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindAuthSessionPersistence(
        preferences: EncryptedAuthPreferences,
    ): AuthSessionPersistence

    @Binds
    @Singleton
    abstract fun bindRefreshCookiePersistence(
        preferences: EncryptedAuthPreferences,
    ): RefreshCookiePersistence

    @Binds
    @Singleton
    abstract fun bindAuthLogoutAction(
        action: SerializedAuthLogoutAction,
    ): AuthLogoutAction
}
