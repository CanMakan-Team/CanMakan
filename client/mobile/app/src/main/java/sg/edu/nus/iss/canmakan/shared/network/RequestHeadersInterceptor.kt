package sg.edu.nus.iss.canmakan.shared.network

import okhttp3.Interceptor
import okhttp3.Response
import sg.edu.nus.iss.canmakan.BuildConfig

class RequestHeadersInterceptor(
    private val includeNgrokSkipHeader: Boolean = BuildConfig.DEBUG,
    private val userAgent: String = "CanMakan-Android/${BuildConfig.VERSION_NAME}",
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("User-Agent", userAgent)
        if (includeNgrokSkipHeader) {
            builder.header("ngrok-skip-browser-warning", "true")
        }
        return chain.proceed(builder.build())
    }
}
