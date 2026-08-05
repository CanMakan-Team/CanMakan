package sg.edu.nus.iss.canmakan.shared.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import sg.edu.nus.iss.canmakan.BuildConfig
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionApiService
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileApiService
import sg.edu.nus.iss.canmakan.features.product.history.data.ScanHistoryApiService
import sg.edu.nus.iss.canmakan.shared.network.CanMakanApiService
import timber.log.Timber
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/api/"

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                    )
                    .build()
                
                var response = chain.proceed(request)
                var tryCount = 0
                val maxLimit = 3
                
                while (!response.isSuccessful && tryCount < maxLimit) {
                    tryCount++
                    response.close()
                    // Linear backoff: wait 2s, 4s, 6s
                    Thread.sleep(2000L * tryCount)
                    response = chain.proceed(request)
                }
                response
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val configuredBaseUrl = BuildConfig.BASE_URL.trim()
        val baseUrl = if (configuredBaseUrl.isNotEmpty()) configuredBaseUrl else DEFAULT_BASE_URL
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        Timber.tag("NetworkModule").d("Initializing Retrofit with base URL: $normalizedBaseUrl")

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideDietaryRestrictionApiService(retrofit: Retrofit): DietaryRestrictionApiService {
        return retrofit.create(DietaryRestrictionApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideFamilyProfileApiService(retrofit: Retrofit): FamilyProfileApiService {
        return retrofit.create(FamilyProfileApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideCanMakanApiService(retrofit: Retrofit): CanMakanApiService {
        return retrofit.create(CanMakanApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideScanHistoryApiService(retrofit: Retrofit): ScanHistoryApiService {
        return retrofit.create(ScanHistoryApiService::class.java)
    }
}
