package com.alexpo.grammermate.data.validation

import android.util.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private const val TAG = "DataValidator"
private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Validate ISO date string format (yyyy-MM-dd).
 */
internal fun validateIsoDate(dateString: String?): String {
    if (dateString == null) return ""
    if (dateString.isBlank()) return ""
    return try {
        LocalDate.parse(dateString, DATE_FORMATTER)
        dateString
    } catch (e: DateTimeParseException) {
        Log.w(TAG, "Invalid ISO date format: $dateString")
        ""
    }
}
