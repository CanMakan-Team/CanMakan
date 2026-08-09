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
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import sg.edu.nus.iss.canmakan.BuildConfig
import sg.edu.nus.iss.canmakan.features.auth.data.AuthApiService
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRepository
import sg.edu.nus.iss.canmakan.features.auth.data.RefreshApiService
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationApiService
import sg.edu.nus.iss.canmakan.features.auth.session.AuthRefreshClient
import sg.edu.nus.iss.canmakan.features.auth.session.AuthRefreshCoordinator
import sg.edu.nus.iss.canmakan.features.auth.session.AuthLogoutClient
import sg.edu.nus.iss.canmakan.features.auth.session.AuthRequestPolicy
import sg.edu.nus.iss.canmakan.features.auth.session.AuthRestorer
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionRestorer
import sg.edu.nus.iss.canmakan.features.auth.session.BearerAuthInterceptor
import sg.edu.nus.iss.canmakan.features.auth.session.BearerAuthenticator
import sg.edu.nus.iss.canmakan.features.auth.session.PersistentRefreshCookieJar
import sg.edu.nus.iss.canmakan.features.auth.session.RetrofitAuthRefreshClient
import sg.edu.nus.iss.canmakan.features.auth.session.RetrofitAuthLogoutClient
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionApiService
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileApiService
import sg.edu.nus.iss.canmakan.features.product.history.data.ScanHistoryApiService
import sg.edu.nus.iss.canmakan.shared.network.CanMakanApiService
import sg.edu.nus.iss.canmakan.shared.util.BACKEND_LOCAL_DATE_TIME_FORMATTER
import timber.log.Timber
import java.net.Proxy
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val NO_RETRY_HEADER = "X-CanMakan-No-Retry"
    private const val AUTH_REFRESH_NETWORK = "AuthRefreshNetwork"
    private val HTTP_CLIENT_ERROR_RANGE = 400..499

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
            // Registration transmits plaintext credentials in its request body.
            // BASIC retains method/status diagnostics without logging any bodies.
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    @Provides
    @Singleton
    fun provideCookieJar(refreshCookieJar: PersistentRefreshCookieJar): CookieJar {
        return refreshCookieJar
    }

    @Provides
    @Singleton
    fun provideApiBaseUrl(): HttpUrl = resolveBaseUrl()

    @Provides
    @Singleton
    fun provideAuthRequestPolicy(apiBaseUrl: HttpUrl): AuthRequestPolicy {
        return AuthRequestPolicy(apiBaseUrl)
    }

    @Provides
    @Singleton
    fun provideBearerAuthInterceptor(
        authSessionStore: AuthSessionStore,
        authRequestPolicy: AuthRequestPolicy,
    ): BearerAuthInterceptor {
        return BearerAuthInterceptor(authSessionStore, authRequestPolicy)
    }

    @Provides
    @Singleton
    @Named(AUTH_REFRESH_NETWORK)
    fun provideAuthRefreshOkHttpClient(
        refreshCookieJar: PersistentRefreshCookieJar,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .cookieJar(refreshCookieJar)
            .retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named(AUTH_REFRESH_NETWORK)
    fun provideAuthRefreshRetrofit(
        @Named(AUTH_REFRESH_NETWORK) refreshOkHttpClient: OkHttpClient,
        gson: Gson,
        apiBaseUrl: HttpUrl,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(refreshOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideRefreshApiService(
        @Named(AUTH_REFRESH_NETWORK) refreshRetrofit: Retrofit,
    ): RefreshApiService {
        return refreshRetrofit.create(RefreshApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthRefreshClient(
        refreshApiService: RefreshApiService,
    ): AuthRefreshClient {
        return RetrofitAuthRefreshClient(refreshApiService)
    }

    @Provides
    @Singleton
    fun provideAuthLogoutClient(
        refreshApiService: RefreshApiService,
    ): AuthLogoutClient {
        return RetrofitAuthLogoutClient(refreshApiService)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        cookieJar: CookieJar,
        bearerAuthInterceptor: BearerAuthInterceptor,
        bearerAuthenticator: BearerAuthenticator,
    ): OkHttpClient {
        return buildOkHttpClient(
            loggingInterceptor,
            cookieJar,
            bearerAuthInterceptor,
            bearerAuthenticator,
        )
    }

    // Retains the 7.5 interceptor-test entry point without constructing refresh coordination.
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        cookieJar: CookieJar,
        bearerAuthInterceptor: BearerAuthInterceptor,
    ): OkHttpClient {
        return buildOkHttpClient(loggingInterceptor, cookieJar, bearerAuthInterceptor, null)
    }

    // Retains the narrow UC18 unit-test entry point without constructing auth storage.
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return buildOkHttpClient(loggingInterceptor, CookieJar.NO_COOKIES, null, null)
    }

    private fun buildOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        cookieJar: CookieJar,
        bearerAuthInterceptor: BearerAuthInterceptor?,
        authenticator: Authenticator?,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .cookieJar(cookieJar)

        if (bearerAuthInterceptor != null) {
            builder.addInterceptor(bearerAuthInterceptor)
        }

        if (authenticator != null) {
            builder.authenticator(authenticator)
        }

        return builder
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val skipRetries = originalRequest.header(NO_RETRY_HEADER)
                    .equals("true", ignoreCase = true)
                val request = originalRequest.newBuilder()
                    .removeHeader(NO_RETRY_HEADER)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                    )
                    .build()

                var response: okhttp3.Response? = null
                var tryCount = 0
                val maxLimit = if (skipRetries) 0 else 2 // Total 3 attempts by default.
                var lastException: java.io.IOException? = null

                while (tryCount <= maxLimit) {
                    try {
                        response?.close()
                        response = chain.proceed(request)
                        if (response.isSuccessful ||
                            response.code in HTTP_CLIENT_ERROR_RANGE ||
                            response.priorResponse != null
                        ) {
                            return@addInterceptor response
                        }
                    } catch (e: java.io.IOException) {
                        lastException = e
                        Timber.tag("NetworkModule").w("Request failed on attempt ${tryCount + 1}")
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
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        apiBaseUrl: HttpUrl,
    ): Retrofit {
        Timber.tag("NetworkModule").d("Initializing Retrofit with base URL: $apiBaseUrl")

        return Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private fun resolveBaseUrl(): HttpUrl {
        val configuredBaseUrl = BuildConfig.BASE_URL.trim()
        require(configuredBaseUrl.isNotEmpty()) { "Android API base URL is not configured." }
        val normalizedBaseUrl = if (configuredBaseUrl.endsWith("/")) {
            configuredBaseUrl
        } else {
            "$configuredBaseUrl/"
        }
        return normalizedBaseUrl.toHttpUrl()
    }

    @Provides
    @Singleton
    fun provideDietaryRestrictionApiService(retrofit: Retrofit): DietaryRestrictionApiService {
        return retrofit.create(DietaryRestrictionApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideRegistrationApiService(retrofit: Retrofit): RegistrationApiService {
        return retrofit.create(RegistrationApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthSessionRestorer(
        authRepository: AuthRepository,
        authSessionStore: AuthSessionStore,
        refreshCoordinator: AuthRefreshCoordinator,
        refreshCookieJar: PersistentRefreshCookieJar,
        apiBaseUrl: HttpUrl,
    ): AuthRestorer {
        return AuthSessionRestorer(
            authRepository = authRepository,
            authSessionStore = authSessionStore,
            refreshCoordinator = refreshCoordinator,
            refreshCookieJar = refreshCookieJar,
            refreshUrl = requireNotNull(apiBaseUrl.resolve("auth/refresh")),
        )
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
