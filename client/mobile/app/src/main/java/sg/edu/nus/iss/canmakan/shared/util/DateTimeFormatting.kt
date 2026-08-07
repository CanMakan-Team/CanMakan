package sg.edu.nus.iss.canmakan.shared.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Format used for all local date-time fields sent to/from the backend.
 * Example: "2026-08-07T20:55:06"
 */
val BACKEND_LOCAL_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)

/**
 * Format used for displaying timestamps in the scan history and other UI elements.
 * Example: "7 Aug, 8:55 PM"
 */
private val SCAN_HISTORY_DISPLAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

/**
 * Converts a [LocalDateTime] to a human-readable string for display in the scan history.
 */
fun LocalDateTime.toScanHistoryDisplayString(): String {
    return this.format(SCAN_HISTORY_DISPLAY_FORMATTER)
}
