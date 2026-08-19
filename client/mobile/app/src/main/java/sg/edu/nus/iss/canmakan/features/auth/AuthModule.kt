package sg.edu.nus.iss.canmakan.features.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    companion object {
        /**
         * AndroidX Security Crypto 1.1.0 deprecates EncryptedSharedPreferences but does not
         * provide a replacement. The master key stays in Android Keystore.
         */
        @Suppress("DEPRECATION")
        @Provides
        @Singleton
        fun provideEncryptedAuthPreferences(
            @ApplicationContext context: Context,
        ): EncryptedAuthPreferences {
            return EncryptedAuthPreferences { createEncryptedPreferences(context) }
        }

        @Suppress("DEPRECATION")
        private fun createEncryptedPreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                AUTH_SECURE_PREFERENCES_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        private const val AUTH_SECURE_PREFERENCES_FILE = "canmakan_auth_secure"
    }
}
