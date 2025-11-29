package no.vegvesen.nvdb.tnits.katalog.core.model

import java.io.InputStream

data class FileDownload(
    val inputStream: InputStream,
    val fileName: String,
    val contentType: String,
    val size: Long,
)
