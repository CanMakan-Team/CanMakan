package sg.edu.nus.iss.canmakan.shared.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale

// Renders am/pm lowercase with no separating space, e.g. "2.54pm" instead of "2:54 PM".
private val AM_PM_TEXT = mapOf(0L to "am", 1L to "pm")

// e.g. "26 Aug 2026, 2.54pm"
private val SCAN_HISTORY_DISPLAY_FORMATTER = DateTimeFormatterBuilder()
    .appendPattern("d MMM yyyy, h.mm")
    .appendText(ChronoField.AMPM_OF_DAY, AM_PM_TEXT)
    .toFormatter(Locale.ENGLISH)

/**
 * Formats a scan timestamp sent by the backend (LocalDateTime.toString() shape, e.g.
 * "2026-08-06T23:51:12") for display, e.g. "26 Aug 2026, 2.54pm". Falls back to the raw
 * string if it can't be parsed, so an unexpected format still renders instead of crashing
 * the screen.
 */
fun String.toScanHistoryDisplayString(): String {
    return try {
        LocalDateTime.parse(this).format(SCAN_HISTORY_DISPLAY_FORMATTER)
    } catch (e: DateTimeParseException) {
        this
    }
}
