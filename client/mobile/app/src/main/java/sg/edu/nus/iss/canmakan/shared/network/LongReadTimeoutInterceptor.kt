package sg.edu.nus.iss.canmakan.shared.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Scan assess and recommendations can wait on Open Food Facts / Tavily / catalog
 * checks. The shared 15s read timeout otherwise aborts the first attempt and
 * [RetryPolicyInterceptor] starts a second overlapping request.
 */
class LongReadTimeoutInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        if (!needsLongRead(path)) {
            return chain.proceed(chain.request())
        }
        return chain
            .withReadTimeout(LONG_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .proceed(chain.request())
    }

    private fun needsLongRead(encodedPath: String): Boolean {
        return encodedPath.contains("/scan/assess") || encodedPath.endsWith("/recommendations")
    }

    companion object {
        const val LONG_READ_TIMEOUT_SECONDS = 60
    }
}
