package no.vegvesen.nvdb.tnits.extensions

import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.toJavaInstant

val OsloZoneId: ZoneId = ZoneId.of("Europe/Oslo")

fun Clock.nowOffsetDateTime() =
    this
        .now()
        .toJavaInstant()
        .atZone(OsloZoneId)
        .toOffsetDateTime()
