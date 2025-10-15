package no.vegvesen.nvdb.tnits.katalog.core.model

import java.time.Instant

data class FileObject(
    val objectName: String,
    val timestamp: Instant,
)
