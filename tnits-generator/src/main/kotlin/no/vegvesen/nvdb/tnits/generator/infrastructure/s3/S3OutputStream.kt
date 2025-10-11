package no.vegvesen.nvdb.tnits.generator.infrastructure.s3

import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.*

private val log = LoggerFactory.getLogger(S3OutputStream::class.java)

class S3OutputStream(
    private val minioClient: MinioClient,
    private val bucket: String,
    private val objectKey: String,
    private val contentType: String,
    private val uploadTimeoutMinutes: Long = 10,
) : OutputStream() {

    private val pipedOutputStream = PipedOutputStream()
    private val pipedInputStream = PipedInputStream(pipedOutputStream, PIPE_BUFFER_SIZE)
    private val uploadFuture = CompletableFuture<Unit>()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "S3Upload-$bucket-$objectKey").apply {
            isDaemon = true
        }
    }

    @Volatile
    private var closed = false

    init {
        startUpload()
    }

    private fun startUpload() {
        executor.submit {
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

        try {
            uploadFuture.get(uploadTimeoutMinutes, TimeUnit.MINUTES)
            log.debug("S3 upload completed during close(): s3://$bucket/$objectKey")
        } catch (e: TimeoutException) {
            log.error("S3 upload timed out after $uploadTimeoutMinutes minutes: s3://$bucket/$objectKey")
            throw IOException("S3 upload timed out after $uploadTimeoutMinutes minutes", e)
        } catch (e: Exception) {
            log.error("S3 upload failed during close(): s3://$bucket/$objectKey", e)
            throw IOException("S3 upload failed: ${e.message}", e.cause ?: e)
        } finally {
            shutdownExecutor()
        }
    }

    private fun shutdownExecutor() {
        try {
            executor.shutdown()
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate gracefully, forcing shutdown")
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            log.warn("Interrupted while waiting for executor shutdown")
            executor.shutdownNow()
            Thread.currentThread().interrupt()
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
