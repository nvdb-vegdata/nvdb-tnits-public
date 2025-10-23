package no.vegvesen.nvdb.tnits.generator.core.extensions

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

val OsloZone = TimeZone.of("Europe/Oslo")

fun Instant.truncateToSeconds() = Instant.fromEpochSeconds(epochSeconds)

/**
 * Returns today's date in the Oslo time zone.
 */
fun Clock.today() = todayIn(OsloZone)
