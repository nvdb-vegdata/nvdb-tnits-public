package no.vegvesen.nvdb.tnits

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.minio.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import no.vegvesen.nvdb.tnits.config.ExportTarget
import no.vegvesen.nvdb.tnits.config.ExporterConfig
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.geometry.parseWkt
import no.vegvesen.nvdb.tnits.storage.S3OutputStream
import org.testcontainers.containers.MinIOContainer
import java.util.zip.GZIPInputStream
import kotlin.time.Instant

class S3IntegrationTest :
    StringSpec({

        val minioContainer: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z")
            .withUserName("testuser")
            .withPassword("testpassword")
        lateinit var minioClient: MinioClient
        val testBucket = "nvdb-tnits-test"

        val exporterConfig = ExporterConfig(false, ExportTarget.S3, testBucket)

        beforeSpec {
            minioContainer.start()

            minioClient = MinioClient.builder()
                .endpoint(minioContainer.s3URL)
                .credentials(minioContainer.userName, minioContainer.password)
                .build()

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
        "S3OutputStream should upload data to real MinIO instance" {
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

        "S3OutputStream should handle GZIP compression correctly" {
            // Arrange
            val testData = "This is a test for GZIP compression in S3OutputStream!"
            val objectKey = "test-uploads/gzip-test.txt.gz"

            // Act
            S3OutputStream(minioClient, testBucket, objectKey, "application/gzip").use { outputStream ->
                java.util.zip.GZIPOutputStream(outputStream).use { gzipStream ->
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

        "SpeedLimitExporter should export to S3 with correct folder structure" {
            // Arrange
            val mockSpeedLimit = SpeedLimit(
                id = 123L,
                kmh = 80,
                validFrom = kotlinx.datetime.LocalDate(2025, 1, 15),
                validTo = null,
                beginLifespanVersion = Instant.parse("2025-01-15T10:30:00Z"),
                updateType = UpdateType.Add,
                geometry = parseWkt("LINESTRING (500000 6600000, 500100 6600100)", SRID.UTM33),
                locationReferences = emptyList(),
            )

            val mockGenerator = mockk<SpeedLimitGenerator> {
                every { generateSpeedLimitsSnapshot() } returns flowOf(mockSpeedLimit)
            }

            val exporter = SpeedLimitExporter(mockGenerator, exporterConfig, minioClient)
            val exportTimestamp = Instant.parse("2025-01-15T10:30:00Z")

            // Act
            exporter.exportSpeedLimitsFullSnapshot(exportTimestamp)

            // Assert - verify object was uploaded with correct key
            val expectedKey = "0105-speed-limits/2025-01-15T10-30-00Z/snapshot.xml"

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

        "SpeedLimitExporter should export GZIP compressed files to S3" {
            // Arrange
            val mockSpeedLimit = SpeedLimit(
                id = 456L,
                kmh = 60,
                validFrom = kotlinx.datetime.LocalDate(2025, 1, 15),
                validTo = null,
                beginLifespanVersion = Instant.parse("2025-01-15T10:30:00Z"),
                updateType = UpdateType.Add,
                geometry = parseWkt("LINESTRING (501000 6601000, 501100 6601100)", SRID.UTM33),
                locationReferences = emptyList(),
            )

            val mockGenerator = mockk<SpeedLimitGenerator> {
                every { generateSpeedLimitsSnapshot() } returns flowOf(mockSpeedLimit)
            }

            val appConfig = exporterConfig.copy(gzip = true)

            val exporter = SpeedLimitExporter(mockGenerator, appConfig, minioClient)
            val exportTimestamp = Instant.parse("2025-01-15T11:45:30Z")

            // Act
            exporter.exportSpeedLimitsFullSnapshot(exportTimestamp)

            // Assert - verify GZIP object was uploaded
            val expectedKey = "0105-speed-limits/2025-01-15T11-45-30Z/snapshot.xml.gz"

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
