package sg.edu.nus.iss.canmakan.shared.util

import java.net.ConnectException
import java.net.SocketTimeoutException

fun userMessageForNetworkFailure(
    exception: Throwable,
    fallback: String = "Unable to connect to the server. Please check your network and try again.",
): String {
    return when (exception) {
        is SocketTimeoutException ->
            "Connection timed out. Please check the configured backend connection."
        is ConnectException ->
            "Failed to connect to the server. Please check your network."
        else -> fallback
    }
}
