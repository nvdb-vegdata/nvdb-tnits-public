package no.vegvesen.nvdb.tnits

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.minio.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.tnits.config.ExportTarget
import no.vegvesen.nvdb.tnits.config.ExporterConfig
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.geometry.parseWkt
import no.vegvesen.nvdb.tnits.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.model.IntProperty
import no.vegvesen.nvdb.tnits.model.RoadFeaturePropertyType
import no.vegvesen.nvdb.tnits.model.TnitsFeature
import no.vegvesen.nvdb.tnits.storage.S3OutputStream
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.time.Instant

class S3IntegrationTest :
    ShouldSpec({

        val minioContainer: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z")
            .withUserName("testuser")
            .withPassword("testpassword")
            .waitingFor(
                Wait.forHttp("/minio/health/live")
                    .forPort(9000)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(120)),
            )
        lateinit var minioClient: MinioClient
        val testBucket = "nvdb-tnits-test"

        val exporterConfig = ExporterConfig(false, ExportTarget.S3, testBucket)

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

        beforeSpec {
            minioContainer.start()

            minioClient = MinioClient.builder()
                .endpoint(minioContainer.s3URL)
                .credentials(minioContainer.userName, minioContainer.password)
                .build()

            waitForMinioReady(minioClient)

            // Create test bucket
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(testBucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(testBucket).build())
            }
        }

        afterSpec {
            minioContainer.stop()
        }

        beforeEach {
            // Clear all objects from test bucket before each test
            val objects = minioClient.listObjects(ListObjectsArgs.builder().bucket(testBucket).build())
            for (result in objects) {
                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(testBucket)
                        .`object`(result.get().objectName())
                        .build(),
                )
            }
        }
        should("upload data to real MinIO instance") {
            // Arrange
            val testData = "Hello, MinIO Integration Test!"
            val objectKey = "test-uploads/integration-test.txt"

            // Act
            S3OutputStream(minioClient, testBucket, objectKey, "text/plain").use { outputStream ->
                outputStream.write(testData.toByteArray())
            }

            // Assert - verify object exists and has correct content
            val statResponse = minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(testBucket)
                    .`object`(objectKey)
                    .build(),
            )

            statResponse shouldNotBe null
            statResponse.size() shouldBe testData.length.toLong()

            // Verify content
            val retrievedContent = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(testBucket)
                    .`object`(objectKey)
                    .build(),
            ).use { it.readBytes().decodeToString() }

            retrievedContent shouldBe testData
        }

        should("handle GZIP compression correctly") {
            // Arrange
            val testData = "This is a test for GZIP compression in S3OutputStream!"
            val objectKey = "test-uploads/gzip-test.txt.gz"

            // Act
            S3OutputStream(minioClient, testBucket, objectKey, "application/gzip").use { outputStream ->
                GZIPOutputStream(outputStream).use { gzipStream ->
                    gzipStream.write(testData.toByteArray())
                }
            }

            // Assert - verify object exists
            val statResponse = minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(testBucket)
                    .`object`(objectKey)
                    .build(),
            )

            statResponse shouldNotBe null
            statResponse.contentType() shouldBe "application/gzip"

            // Verify content can be decompressed
            val compressedContent = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(testBucket)
                    .`object`(objectKey)
                    .build(),
            ).use { it.readBytes() }

            val decompressedContent = GZIPInputStream(compressedContent.inputStream()).use { gzipStream ->
                gzipStream.readBytes().decodeToString()
            }

            decompressedContent shouldBe testData
        }

        should("export to S3 with correct folder structure") {
            // Arrange
            val mockSpeedLimit = TnitsFeature(
                id = 123L,
                validFrom = LocalDate(2025, 1, 15),
                validTo = null,
                beginLifespanVersion = Instant.parse("2025-01-15T10:30:00Z"),
                updateType = UpdateType.Add,
                geometry = parseWkt("LINESTRING (500000 6600000, 500100 6600100)", SRID.UTM33),
                type = ExportedFeatureType.SpeedLimit,
                properties = mapOf(
                    RoadFeaturePropertyType.MaximumSpeedLimit to IntProperty(80),
                ),
                openLrLocationReferences = emptyList(),
                nvdbLocationReferences = emptyList(),
            )

            val mockGenerator = mockk<TnitsFeatureGenerator> {
                every { generateSnapshot(any()) } returns flowOf(mockSpeedLimit)
            }

            val exporter = TnitsFeatureExporter(mockGenerator, exporterConfig, minioClient, mockk(), mockk(), mockk(relaxed = true))
            val exportTimestamp = Instant.parse("2025-01-15T10:30:00Z")

            // Act
            exporter.exportSnapshot(exportTimestamp, ExportedFeatureType.SpeedLimit)

            // Assert - verify object was uploaded with correct key
            val expectedKey = "0105-speedLimit/2025-01-15T10-30-00Z/snapshot.xml"

            val statResponse = minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(testBucket)
                    .`object`(expectedKey)
                    .build(),
            )

            statResponse shouldNotBe null
            statResponse.contentType() shouldBe "application/xml"

            // Verify XML content contains our test data
            val xmlContent = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(testBucket)
                    .`object`(expectedKey)
                    .build(),
            ).use { it.readBytes().decodeToString() }

            xmlContent.contains("123") shouldBe true
            xmlContent.contains("80") shouldBe true
            xmlContent.contains("RoadFeatureDataset") shouldBe true
        }

        should("export GZIP compressed files to S3") {
            // Arrange
            val mockSpeedLimit = TnitsFeature(
                id = 456L,
                type = ExportedFeatureType.SpeedLimit,
                properties = mapOf(
                    RoadFeaturePropertyType.MaximumSpeedLimit to IntProperty(60),
                ),
                validFrom = LocalDate(2025, 1, 15),
                validTo = null,
                beginLifespanVersion = Instant.parse("2025-01-15T10:30:00Z"),
                updateType = UpdateType.Add,
                geometry = parseWkt("LINESTRING (501000 6601000, 501100 6601100)", SRID.UTM33),
                openLrLocationReferences = emptyList(),
                nvdbLocationReferences = emptyList(),
            )

            val mockGenerator = mockk<TnitsFeatureGenerator> {
                every { generateSnapshot(any()) } returns flowOf(mockSpeedLimit)
            }

            val appConfig = exporterConfig.copy(gzip = true)

            val exporter = TnitsFeatureExporter(mockGenerator, appConfig, minioClient, mockk(), mockk(), mockk(relaxed = true))
            val exportTimestamp = Instant.parse("2025-01-15T11:45:30Z")

            // Act
            exporter.exportSnapshot(exportTimestamp, ExportedFeatureType.SpeedLimit)

            // Assert - verify GZIP object was uploaded
            val expectedKey = "0105-speedLimit/2025-01-15T11-45-30Z/snapshot.xml.gz"

            val statResponse = minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(testBucket)
                    .`object`(expectedKey)
                    .build(),
            )

            statResponse shouldNotBe null
            statResponse.contentType() shouldBe "application/gzip"

            // Verify compressed content can be decompressed and contains our test data
            val compressedContent = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(testBucket)
                    .`object`(expectedKey)
                    .build(),
            ).use { it.readBytes() }

            val xmlContent = GZIPInputStream(compressedContent.inputStream()).use { gzipStream ->
                gzipStream.readBytes().decodeToString()
            }

            xmlContent.contains("456") shouldBe true
            xmlContent.contains("60") shouldBe true
            xmlContent.contains("RoadFeatureDataset") shouldBe true
        }
    })
