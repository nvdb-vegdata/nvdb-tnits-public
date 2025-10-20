package no.vegvesen.nvdb.tnits.generator.core.extensions

import kotlinx.datetime.TimeZone
import kotlin.time.Instant

val OsloZone = TimeZone.of("Europe/Oslo")

fun Instant.truncateToSeconds() = Instant.fromEpochSeconds(epochSeconds)
