package sg.edu.nus.iss.canmakan.features.auth

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AuthLifecycleModule {

    @Provides
    @AuthIoDispatcher
    fun provideAuthIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
