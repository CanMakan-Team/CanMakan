package sg.edu.nus.iss.canmakan.shared.di

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
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
import sg.edu.nus.iss.canmakan.shared.util.BACKEND_LOCAL_DATE_TIME_FORMATTER
import timber.log.Timber
import java.net.Proxy
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/api/"

    // The backend sends scan timestamps (e.g. Scan.scannedAt) as a fixed-shape ISO-8601
    // string ("yyyy-MM-ddTHH:mm:ss") rather than an epoch/millis value, so the field
    // deserializes to java.time.LocalDateTime here instead of String — this keeps the
    // wall-clock timestamp intact for display without a timezone conversion.
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(
                LocalDateTime::class.java,
                JsonSerializer<LocalDateTime> { src, _, _ ->
                    JsonPrimitive(BACKEND_LOCAL_DATE_TIME_FORMATTER.format(src))
                }
            )
            .registerTypeAdapter(
                LocalDateTime::class.java,
                JsonDeserializer { json, _, _ ->
                    LocalDateTime.parse(json.asString, BACKEND_LOCAL_DATE_TIME_FORMATTER)
                }
            )
            .create()
    }

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

                var response: okhttp3.Response? = null
                var tryCount = 0
                val maxLimit = 2 // Total 3 attempts
                var lastException: java.io.IOException? = null

                while (tryCount <= maxLimit) {
                    try {
                        response?.close()
                        response = chain.proceed(request)
                        if (response.isSuccessful) return@addInterceptor response
                    } catch (e: java.io.IOException) {
                        lastException = e
                        Timber.tag("NetworkModule").w("Request failed (attempt ${tryCount + 1}): ${e.message}")
                    }

                    if (tryCount < maxLimit) {
                        tryCount++
                        // Backoff: 1s, 2s
                        Thread.sleep(1000L * tryCount)
                    } else {
                        break
                    }
                }

                response ?: throw lastException ?: java.io.IOException("Network request failed after retries")
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        val configuredBaseUrl = BuildConfig.BASE_URL.trim()
        val baseUrl = if (configuredBaseUrl.isNotEmpty()) configuredBaseUrl else DEFAULT_BASE_URL
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        Timber.tag("NetworkModule").d("Initializing Retrofit with base URL: $normalizedBaseUrl")

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
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
