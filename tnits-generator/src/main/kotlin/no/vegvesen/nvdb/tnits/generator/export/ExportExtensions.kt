package no.vegvesen.nvdb.tnits.generator.export

import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.generator.config.ExporterConfig
import no.vegvesen.nvdb.tnits.generator.storage.S3OutputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

/**
 * Buffer size for XML export streams (256 KB).
 */
const val XML_EXPORT_BUFFER_SIZE = 256 * 1024

/**
 * Content type for XML files.
 */
const val CONTENT_TYPE_XML = "application/xml"

/**
 * Content type for GZIP compressed files.
 */
const val CONTENT_TYPE_GZIP = "application/gzip"

/**
 * Opens a buffered output stream for writing to a file, with optional GZIP compression.
 *
 * @param path The file path to write to
 * @return OutputStream configured with buffering and optional compression
 */
fun ExporterConfig.openFileStream(path: Path): OutputStream {
    val fileOut = BufferedOutputStream(Files.newOutputStream(path), XML_EXPORT_BUFFER_SIZE)
    return if (gzip) {
        BufferedOutputStream(GZIPOutputStream(fileOut), XML_EXPORT_BUFFER_SIZE)
    } else {
        fileOut
    }
}

/**
 * Opens a buffered output stream for writing to S3, with optional GZIP compression.
 *
 * @param minioClient MinIO client for S3 operations
 * @param objectKey S3 object key (path within bucket)
 * @return OutputStream configured with S3, buffering, and optional compression
 */
fun ExporterConfig.openS3Stream(minioClient: MinioClient, objectKey: String): OutputStream {
    val contentType = if (gzip) CONTENT_TYPE_GZIP else CONTENT_TYPE_XML
    val s3Stream = S3OutputStream(minioClient, bucket, objectKey, contentType)

    return if (gzip) {
        BufferedOutputStream(GZIPOutputStream(s3Stream), XML_EXPORT_BUFFER_SIZE)
    } else {
        s3Stream
    }
}
