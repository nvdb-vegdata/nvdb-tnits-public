package no.vegvesen.nvdb.tnits.generator.infrastructure.s3

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.minio.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.tnits.common.MinioTestHelper
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.common.model.S3Config
import no.vegvesen.nvdb.tnits.generator.config.ExporterConfig
import no.vegvesen.nvdb.tnits.generator.core.api.TnitsFeatureExporter
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID
import no.vegvesen.nvdb.tnits.generator.core.extensions.parseWkt
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.IntProperty
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.RoadFeaturePropertyType
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsFeature
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.UpdateType
import no.vegvesen.nvdb.tnits.generator.core.services.tnits.FeatureExportWriter
import no.vegvesen.nvdb.tnits.generator.core.services.tnits.FeatureTransformer
import no.vegvesen.nvdb.tnits.generator.core.services.tnits.TnitsExportService
import org.testcontainers.containers.MinIOContainer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.time.Instant

class S3IntegrationTest : ShouldSpec() {

    private val minioContainer: MinIOContainer = MinioTestHelper.createMinioContainer()
    private lateinit var minioClient: MinioClient
    private val testBucket = "nvdb-tnits-test"
    private val exporterConfig = ExporterConfig(false)
    private val s3Config = S3Config(
        endpoint = "",
        accessKey = "",
        secretKey = "",
        bucket = testBucket,
    )

    private lateinit var featureExporter: TnitsFeatureExporter
    private lateinit var exportWriter: FeatureExportWriter

    init {
        beforeSpec {
            minioContainer.start()
            minioClient = MinioTestHelper.createMinioClient(minioContainer)
            MinioTestHelper.waitForMinioReady(minioClient)
            MinioTestHelper.ensureBucketExists(minioClient, testBucket)
            featureExporter = TnitsFeatureS3Exporter(exporterConfig, minioClient, s3Config)
            exportWriter = FeatureExportWriter(featureExporter, mockk(relaxed = true))
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

            val mockGenerator = mockk<FeatureTransformer> {
                every { generateSnapshot(any()) } returns flowOf(mockSpeedLimit)
            }

            val exporter = TnitsExportService(mockGenerator, exportWriter, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
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

            val mockGenerator = mockk<FeatureTransformer> {
                every { generateSnapshot(any()) } returns flowOf(mockSpeedLimit)
            }

            val exportWriter = FeatureExportWriter(
                TnitsFeatureS3Exporter(ExporterConfig(gzip = true), minioClient, s3Config),
                mockk(relaxed = true),
            )

            val exporter = TnitsExportService(mockGenerator, exportWriter, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
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
    }
}
