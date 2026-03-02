package no.vegvesen.nvdb.tnits.katalog.config

import no.vegvesen.nvdb.tnits.common.extensions.OsloZoneId
import org.springframework.core.convert.converter.Converter
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Converter for Instant that handles both ISO-8601 timestamps and date strings.
 * Date strings are interpreted as midnight in Europe/Oslo and converted to Instant.
 */
object InstantConverter : Converter<String, Instant> {
    private val dateFormatter = DateTimeFormatter.ISO_DATE

    override fun convert(source: String): Instant = try {
        // First try to parse as a regular Instant
        Instant.parse(source)
    } catch (_: DateTimeParseException) {
        try {
            // Then try ISO-8601 timestamp with an explicit offset
            OffsetDateTime.parse(source).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                // If that fails, parse as a date at Oslo midnight and convert to Instant
                val localDate = LocalDate.parse(source, dateFormatter)
                localDate.atStartOfDay(OsloZoneId).toInstant()
            } catch (e: DateTimeParseException) {
                // If all parsing attempts fail, throw an exception
                throw IllegalArgumentException(
                    "Invalid date or timestamp format: $source. Expected ISO-8601 timestamp or date (YYYY-MM-DD).",
                    e,
                )
            }
        }
    }
}
