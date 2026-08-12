package sg.edu.nus.iss.canmakan.features.product.recommendation.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecommendationHistoryRepoModule {
    @Binds
    @Singleton
    abstract fun bindRecommendationHistoryRepository(
        repository: ServerRecommendationHistoryRepository
    ): RecommendationHistoryRepository
}
