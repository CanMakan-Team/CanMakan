package sg.edu.nus.iss.canmakan.shared.network

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

/**
 * Retries failed calls immediately (no thread sleep). Client errors (4xx) and
 * responses that already followed an authentication challenge are not retried.
 */
class RetryPolicyInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val skipRetries = originalRequest.header(NO_RETRY_HEADER)
            .equals("true", ignoreCase = true)
        val request = originalRequest.newBuilder()
            .removeHeader(NO_RETRY_HEADER)
            .build()

        var response: Response? = null
        var tryCount = 0
        val maxLimit = if (skipRetries) 0 else 2
        var lastException: IOException? = null

        while (tryCount <= maxLimit) {
            try {
                response?.close()
                response = chain.proceed(request)
                if (response.isSuccessful ||
                    response.code in HTTP_CLIENT_ERROR_RANGE ||
                    response.priorResponse != null
                ) {
                    return response
                }
            } catch (exception: IOException) {
                lastException = exception
                Timber.tag("RetryPolicyInterceptor").w("Request failed on attempt ${tryCount + 1}")
            }

            if (tryCount < maxLimit) {
                tryCount++
            } else {
                break
            }
        }

        return response ?: throw lastException ?: IOException("Network request failed after retries")
    }

    companion object {
        const val NO_RETRY_HEADER = "X-CanMakan-No-Retry"
        private val HTTP_CLIENT_ERROR_RANGE = 400..499
    }
}
