package sg.edu.nus.iss.canmakan.shared.util

import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DateTimeFormattingTest {

    @Test
    fun backendFormatterRoundTripsLocalDateTime() {
        val parsed = LocalDateTime.parse("2026-08-07T20:55:06", BACKEND_LOCAL_DATE_TIME_FORMATTER)
        assertEquals(LocalDateTime.of(2026, 8, 7, 20, 55, 6), parsed)
        assertEquals("2026-08-07T20:55:06", parsed.format(BACKEND_LOCAL_DATE_TIME_FORMATTER))
    }

    @Test
    fun scanHistoryDisplayUsesDayMonthAnd12HourClock() {
        val stamped = LocalDateTime.of(2026, 8, 7, 20, 55, 6)
        assertEquals("7 Aug, 8:55 PM", stamped.toScanHistoryDisplayString())
    }
}
