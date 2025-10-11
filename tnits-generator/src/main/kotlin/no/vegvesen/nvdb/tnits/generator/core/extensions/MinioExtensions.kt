package no.vegvesen.nvdb.tnits.generator.core.extensions

import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.generator.infrastructure.s3.S3OutputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

const val S3_BUFFER_SIZE = 256 * 1024

const val CONTENT_TYPE_XML = "application/xml"

const val CONTENT_TYPE_GZIP = "application/gzip"

/**
 * Opens a buffered output stream for writing to S3, with optional GZIP compression.
 */
fun MinioClient.openS3Stream(bucket: String, objectKey: String, gzip: Boolean, bufferSize: Int = S3_BUFFER_SIZE): OutputStream {
    val contentType = if (gzip) CONTENT_TYPE_GZIP else CONTENT_TYPE_XML
    val s3Stream = S3OutputStream(this, bucket, objectKey, contentType)

    return if (gzip) {
        BufferedOutputStream(GZIPOutputStream(s3Stream), bufferSize)
    } else {
        s3Stream
    }
}
