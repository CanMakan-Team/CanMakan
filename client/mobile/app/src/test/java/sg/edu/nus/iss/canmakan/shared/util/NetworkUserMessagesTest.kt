package sg.edu.nus.iss.canmakan.shared.util

import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetworkUserMessagesTest {

    @Test
    fun timeoutUsesConnectionMessage() {
        assertEquals(
            "Connection timed out. Please check the configured backend connection.",
            userMessageForNetworkFailure(SocketTimeoutException("timed out")),
        )
    }

    @Test
    fun connectFailureUsesNetworkMessage() {
        assertEquals(
            "Failed to connect to the server. Please check your network.",
            userMessageForNetworkFailure(ConnectException("refused")),
        )
    }

    @Test
    fun otherFailuresUseFallback() {
        assertEquals(
            "Unable to connect to the server. Please check your network and try again.",
            userMessageForNetworkFailure(IllegalStateException("boom")),
        )
        assertEquals(
            "Try again later.",
            userMessageForNetworkFailure(RuntimeException("boom"), fallback = "Try again later."),
        )
    }
}
