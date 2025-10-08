package no.vegvesen.nvdb.tnits.generator.extensions

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

val OsloZoneId: ZoneId = ZoneId.of("Europe/Oslo")
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

val today by lazy { Clock.System.todayIn(OsloZone) }

fun Instant.truncateToSeconds() = Instant.fromEpochSeconds(epochSeconds)
