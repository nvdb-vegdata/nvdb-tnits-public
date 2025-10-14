package no.vegvesen.nvdb.tnits.katalog.core.model

import kotlin.time.Instant

data class FileObject(
    val objectName: String,
    val timestamp: Instant,
)
