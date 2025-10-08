package no.vegvesen.nvdb.tnits.generator

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration

object MinioTestHelper {
    const val MINIO_IMAGE = "minio/minio:RELEASE.2025-09-07T16-13-09Z"
    const val DEFAULT_USERNAME = "testuser"
    const val DEFAULT_PASSWORD = "testpassword"
    private const val STARTUP_TIMEOUT_SECONDS = 120L

    fun createMinioContainer(username: String = DEFAULT_USERNAME, password: String = DEFAULT_PASSWORD, portBindings: List<String>? = null): MinIOContainer =
        MinIOContainer(MINIO_IMAGE)
            .withUserName(username)
            .withPassword(password)
            .waitingFor(
                Wait.forHttp("/minio/health/live")
                    .forPort(9000)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(STARTUP_TIMEOUT_SECONDS)),
            )
            .apply {
                portBindings?.let { this.portBindings = it }
            }

    fun createMinioClient(container: MinIOContainer): MinioClient = MinioClient.builder()
        .endpoint(container.s3URL)
        .credentials(container.userName, container.password)
        .build()

    fun waitForMinioReady(client: MinioClient, timeoutSeconds: Long = 60) {
        val deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L
        while (System.nanoTime() < deadline) {
            try {
                client.listBuckets()
                return
            } catch (_: Exception) {
                Thread.sleep(250)
            }
        }
        error("MinIO not ready within ${timeoutSeconds}s")
    }

    fun ensureBucketExists(client: MinioClient, bucket: String) {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
        }
    }
}
