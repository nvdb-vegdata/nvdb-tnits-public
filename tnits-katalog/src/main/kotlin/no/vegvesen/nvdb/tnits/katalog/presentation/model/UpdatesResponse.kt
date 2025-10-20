package no.vegvesen.nvdb.tnits.katalog.presentation.model

import java.time.Instant

data class UpdatesResponse(
    val updates: List<Update>,
    val newUpdates: String,
)

data class Update(
    val href: String,
    val timestamp: Instant,
    val size: Long,
)
