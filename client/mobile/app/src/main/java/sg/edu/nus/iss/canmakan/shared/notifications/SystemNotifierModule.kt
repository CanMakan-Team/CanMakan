package sg.edu.nus.iss.canmakan.shared.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SystemNotifierModule {

    @Binds
    @Singleton
    abstract fun bindSystemNotifier(
        notifier: AndroidSystemNotifier,
    ): SystemNotifier
}
