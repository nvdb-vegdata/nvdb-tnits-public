package no.vegvesen.nvdb.tnits.storage

import io.minio.MinioClient
import io.minio.PutObjectArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CompletableFuture

private val log = LoggerFactory.getLogger(S3OutputStream::class.java)

class S3OutputStream(
    private val minioClient: MinioClient,
    private val bucket: String,
    private val objectKey: String,
    private val contentType: String = "application/xml",
) : OutputStream() {

    private val pipedOutputStream = PipedOutputStream()
    private val pipedInputStream = PipedInputStream(pipedOutputStream, PIPE_BUFFER_SIZE)
    private val uploadFuture = CompletableFuture<Unit>()
    private var closed = false

    init {
        startUpload()
    }

    private fun startUpload() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                log.info("Starting S3 upload: s3://$bucket/$objectKey")

                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucket)
                        .`object`(objectKey)
                        .stream(pipedInputStream, -1, MULTIPART_SIZE)
                        .contentType(contentType)
                        .build(),
                )

                log.info("S3 upload completed successfully: s3://$bucket/$objectKey")
                uploadFuture.complete(Unit)
            } catch (e: Exception) {
                log.error("S3 upload failed: s3://$bucket/$objectKey", e)
                uploadFuture.completeExceptionally(e)
            } finally {
                try {
                    pipedInputStream.close()
                } catch (e: IOException) {
                    log.warn("Error closing piped input stream", e)
                }
            }
        }
    }

    override fun write(b: Int) {
        checkClosed()
        pipedOutputStream.write(b)
    }

    override fun write(b: ByteArray) {
        checkClosed()
        pipedOutputStream.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        checkClosed()
        pipedOutputStream.write(b, off, len)
    }

    override fun flush() {
        checkClosed()
        pipedOutputStream.flush()
    }

    override fun close() {
        if (closed) return
        closed = true

        try {
            pipedOutputStream.close()
        } catch (e: IOException) {
            log.warn("Error closing piped output stream", e)
        }

        runBlocking {
            try {
                uploadFuture.get()
                log.debug("S3 upload completed during close(): s3://$bucket/$objectKey")
            } catch (e: Exception) {
                log.error("S3 upload failed during close(): s3://$bucket/$objectKey", e)
                throw IOException("S3 upload failed: ${e.message}", e)
            }
        }
    }

    private fun checkClosed() {
        if (closed) {
            throw IOException("Stream is closed")
        }
    }

    companion object {
        private const val PIPE_BUFFER_SIZE = 1024 * 1024 // 1MB pipe buffer
        private const val MULTIPART_SIZE = 10L * 1024 * 1024 // 10MB multipart size
    }
}
