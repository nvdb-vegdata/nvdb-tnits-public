package no.vegvesen.nvdb.tnits.generator.core.extensions

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import no.vegvesen.nvdb.tnits.common.extensions.OsloZoneId
import java.time.OffsetDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

val OsloZone = TimeZone.of("Europe/Oslo")

fun Clock.nowOffsetDateTime(): OffsetDateTime = this
    .now()
    .toJavaInstant()
    .atZone(OsloZoneId)
    .toOffsetDateTime()

fun Instant.toOffsetDateTime(): OffsetDateTime = this
    .toJavaInstant()
    .atZone(OsloZoneId)
    .toOffsetDateTime()

// Lazy because the assumption is that this app runs only once and then shuts down. If the app runs for several days this will be wrong.
val today by lazy { Clock.System.todayIn(OsloZone) }

fun Instant.truncateToSeconds() = Instant.fromEpochSeconds(epochSeconds)
