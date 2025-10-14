package no.vegvesen.nvdb.tnits.katalog.presentation.model

import kotlin.time.Instant

data class SnapshotResponse(
    val href: String,
    val newUpdates: String,
)

data class SnapshotsResponse(
    val snapshots: List<Snapshot>,
)

data class Snapshot(
    val href: String,
    val timestamp: Instant,
)
