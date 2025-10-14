package no.vegvesen.nvdb.tnits.katalog.config

import org.springframework.core.convert.converter.Converter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Converter for Instant that handles both ISO-8601 timestamps and date strings.
 * Date strings are converted to midnight Instant on the given date.
 */
object InstantConverter : Converter<String, Instant> {
    private val dateFormatter = DateTimeFormatter.ISO_DATE

    override fun convert(source: String): Instant = try {
        // First try to parse as a regular Instant
        Instant.parse(source)
    } catch (_: DateTimeParseException) {
        try {
            // If that fails, try to parse as a date and convert to midnight Instant
            val localDate = LocalDate.parse(source, dateFormatter)
            localDate.atStartOfDay(ZoneId.of("UTC")).toInstant()
        } catch (e: DateTimeParseException) {
            // If both parsing attempts fail, throw an exception
            throw IllegalArgumentException("Invalid date or timestamp format: $source. Expected ISO-8601 timestamp or date (YYYY-MM-DD).", e)
        }
    }
}
