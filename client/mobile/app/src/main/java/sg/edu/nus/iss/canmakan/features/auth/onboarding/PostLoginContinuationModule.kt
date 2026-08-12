package sg.edu.nus.iss.canmakan.features.auth.onboarding

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PostLoginContinuationModule {
    @Binds
    @Singleton
    abstract fun bindPendingInvitationClaimer(
        claimer: FamilyPendingInvitationClaimer,
    ): PendingInvitationClaimer
}
