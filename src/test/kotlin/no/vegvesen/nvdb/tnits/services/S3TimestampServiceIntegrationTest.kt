package no.vegvesen.nvdb.tnits.services

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.minio.*
import no.vegvesen.nvdb.tnits.model.ExportedFeatureType
import org.testcontainers.containers.MinIOContainer
import java.io.ByteArrayInputStream
import kotlin.time.Instant

class S3TimestampServiceIntegrationTest :
    StringSpec({

        val minioContainer: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z")
            .withUserName("testuser")
            .withPassword("testpassword")
        minioContainer.portBindings = listOf("60900:9000", "60901:9001")
        lateinit var minioClient: MinioClient
        lateinit var timestampService: S3TimestampService
        val testBucket = "nvdb-tnits-timestamp-test"

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

            timestampService = S3TimestampService(minioClient, testBucket)
        }

        afterSpec {
            minioContainer.stop()
        }

        fun uploadTestFile(objectKey: String, content: String) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(testBucket)
                    .`object`(objectKey)
                    .stream(ByteArrayInputStream(content.toByteArray()), content.length.toLong(), -1)
                    .contentType("application/xml")
                    .build(),
            )
        }

        beforeEach {
            // First, delete all objects in the bucket using bulk deletion
            val objects = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .recursive(true)
                    .bucket(testBucket).build(),
            )
            val objectsToDelete = mutableListOf<io.minio.messages.DeleteObject>()

            for (result in objects) {
                objectsToDelete.add(io.minio.messages.DeleteObject(result.get().objectName()))
            }

            if (objectsToDelete.isNotEmpty()) {
                val deleteResults = minioClient.removeObjects(
                    RemoveObjectsArgs.builder()
                        .bucket(testBucket)
                        .objects(objectsToDelete)
                        .build(),
                )
                // Process results to trigger actual deletion
                for (result in deleteResults) {
                    result.get() // This triggers the deletion
                }
            }
        }

        "should return null when no exports exist in S3" {
            val lastSnapshot = timestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit)
            val lastUpdate = timestampService.getLastUpdateTimestamp(ExportedFeatureType.SpeedLimit)

            lastSnapshot.shouldBeNull()
            lastUpdate.shouldBeNull()
        }

        "should return latest snapshot timestamp from multiple S3 objects" {
            // Arrange - create multiple snapshot exports
            val timestamps = listOf(
                "2025-01-15T10-30-00Z",
                "2025-01-15T14-45-00Z", // This should be the latest
                "2025-01-15T09-15-00Z",
            )

            timestamps.forEach { timestamp ->
                val objectKey = "0105-speedLimit/$timestamp/snapshot.xml.gz"
                uploadTestFile(objectKey, "<xml>test snapshot</xml>")
            }

            // Act
            val result = timestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit)

            // Assert
            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-01-15T14:45:00Z")
        }

        "should return latest update timestamp from multiple S3 objects" {
            // Arrange - create multiple update exports
            val timestamps = listOf(
                "2025-01-15T08-00-00Z",
                "2025-01-15T16-30-00Z", // This should be the latest
                "2025-01-15T12-00-00Z",
            )

            timestamps.forEach { timestamp ->
                val objectKey = "0105-speedLimit/$timestamp/update.xml"
                uploadTestFile(objectKey, "<xml>test update</xml>")
            }

            // Act
            val result = timestampService.getLastUpdateTimestamp(ExportedFeatureType.SpeedLimit)

            // Assert
            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-01-15T16:30:00Z")
        }

        "should distinguish between snapshot and update exports" {
            // Arrange - create both types
            uploadTestFile("0105-speedLimit/2025-01-15T10-00-00Z/snapshot.xml.gz", "<xml>snapshot</xml>")
            uploadTestFile("0105-speedLimit/2025-01-15T11-00-00Z/update.xml.gz", "<xml>update</xml>")
            uploadTestFile("0105-speedLimit/2025-01-15T12-00-00Z/snapshot.xml", "<xml>another snapshot</xml>")

            // Act
            val lastSnapshot = timestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit)
            val lastUpdate = timestampService.getLastUpdateTimestamp(ExportedFeatureType.SpeedLimit)

            // Assert
            lastSnapshot.shouldNotBeNull()
            lastSnapshot shouldBe Instant.parse("2025-01-15T12:00:00Z") // Latest snapshot

            lastUpdate.shouldNotBeNull()
            lastUpdate shouldBe Instant.parse("2025-01-15T11:00:00Z") // Only update
        }

        "should handle mixed file extensions (compressed and uncompressed)" {
            // Arrange
            uploadTestFile("0105-speedLimit/2025-01-15T10-00-00Z/snapshot.xml.gz", "<xml>compressed</xml>")
            uploadTestFile("0105-speedLimit/2025-01-15T11-00-00Z/snapshot.xml", "<xml>uncompressed</xml>")

            // Act
            val result = timestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit)

            // Assert
            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-01-15T11:00:00Z")
        }

        "should ignore files with malformed timestamps" {
            // Arrange
            uploadTestFile("0105-speedLimit/not-a-timestamp/snapshot.xml", "<xml>bad timestamp</xml>")
            uploadTestFile("0105-speedLimit/2025-01-15T10-00-00Z/snapshot.xml", "<xml>good timestamp</xml>")
            uploadTestFile("0105-speedLimit/malformed-date/snapshot.xml", "<xml>another bad one</xml>")

            // Act
            val result = timestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit)

            // Assert - should only find the one with valid timestamp
            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-01-15T10:00:00Z")
        }

        "should ignore non-export files in the folder structure" {
            // Arrange
            uploadTestFile("0105-speedLimit/2025-01-15T10-00-00Z/snapshot.xml", "<xml>real export</xml>")
            uploadTestFile("0105-speedLimit/2025-01-15T10-00-00Z/readme.txt", "This is not an export")
            uploadTestFile("0105-speedLimit/2025-01-15T10-00-00Z/backup.bak", "backup file")
            uploadTestFile("0105-speedLimit/logs/error.log", "log file")

            // Act
            val result = timestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit)

            // Assert - should only find the XML export
            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-01-15T10:00:00Z")
        }

        "should handle empty bucket gracefully" {
            // Act - bucket is already cleared in beforeEach
            val lastSnapshot = timestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit)
            val lastUpdate = timestampService.getLastUpdateTimestamp(ExportedFeatureType.SpeedLimit)

            // Assert
            lastSnapshot.shouldBeNull()
            lastUpdate.shouldBeNull()
        }

        "should handle S3 objects in chronological order" {
            // Arrange - create exports spanning multiple days
            val chronologicalTimestamps = listOf(
                "2025-01-01T00-00-00Z",
                "2025-01-15T12-00-00Z",
                "2025-01-20T18-30-00Z",
                "2025-02-01T09-15-00Z", // This should be latest
            )

            chronologicalTimestamps.forEach { timestamp ->
                val objectKey = "0105-speedLimit/$timestamp/snapshot.xml"
                uploadTestFile(objectKey, "<xml>export at $timestamp</xml>")
            }

            // Act
            val result = timestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit)

            // Assert
            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-02-01T09:15:00Z")
        }
    })
