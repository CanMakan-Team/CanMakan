package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.repo.server

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServerModule {

    // Create the shared Retrofit instance for the whole app
    // @Provides is a Hilt annotation telling the DI mechanism how to
    // create the object
    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://unknown.com/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideDietaryRestrictionApiService(retrofit: Retrofit): DietaryRestrictionApiService {
        return retrofit.create(DietaryRestrictionApiService::class.java)
    }
}