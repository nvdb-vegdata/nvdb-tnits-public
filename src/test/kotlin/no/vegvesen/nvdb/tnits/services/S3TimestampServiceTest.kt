package no.vegvesen.nvdb.tnits.services

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.vegvesen.nvdb.tnits.services.S3TimestampService
import kotlin.time.Instant

class S3TimestampServiceTest :
    StringSpec({

        val service = S3TimestampService(
            minioClient = mockk(), // We'll only test parsing logic, not actual S3 calls
            bucketName = "test-bucket",
        )

        "parseTimestampFromS3Key should parse valid snapshot key" {
            val s3Key = "0105-speed-limits/2025-01-15T10-30-00Z/snapshot.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)

            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-01-15T10:30:00Z")
        }

        "parseTimestampFromS3Key should parse valid update key" {
            val s3Key = "0105-speed-limits/2025-01-15T14-45-30Z/update.xml"
            val result = service.parseTimestampFromS3Key(s3Key)

            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-01-15T14:45:30Z")
        }

        "parseTimestampFromS3Key should handle uncompressed files" {
            val s3Key = "0105-speed-limits/2025-12-31T23-59-59Z/snapshot.xml"
            val result = service.parseTimestampFromS3Key(s3Key)

            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-12-31T23:59:59Z")
        }

        "parseTimestampFromS3Key should handle different vegobjekttype padding" {
            val s3Key = "0001-speed-limits/2025-06-15T12-00-00Z/update.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)

            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-06-15T12:00:00Z")
        }

        "parseTimestampFromS3Key should return null for blank key" {
            val result = service.parseTimestampFromS3Key("")
            result.shouldBeNull()
        }

        "parseTimestampFromS3Key should return null for key with insufficient parts" {
            val s3Key = "0105-speed-limits/snapshot.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)
            result.shouldBeNull()
        }

        "parseTimestampFromS3Key should return null for key with only one part" {
            val s3Key = "0105-speed-limits"
            val result = service.parseTimestampFromS3Key(s3Key)
            result.shouldBeNull()
        }

        "parseTimestampFromS3Key should return null for invalid timestamp format" {
            val s3Key = "0105-speed-limits/not-a-timestamp/snapshot.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)
            result.shouldBeNull()
        }

        "parseTimestampFromS3Key should return null for malformed timestamp" {
            val s3Key = "0105-speed-limits/2025-13-40T25-70-80Z/snapshot.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)
            result.shouldBeNull()
        }

        "parseTimestampFromS3Key should return null for timestamp without T separator" {
            val s3Key = "0105-speed-limits/2025-01-15-10-30-00Z/snapshot.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)
            result.shouldBeNull()
        }

        "parseTimestampFromS3Key should return null for empty timestamp part" {
            val s3Key = "0105-speed-limits//snapshot.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)
            result.shouldBeNull()
        }

        "parseTimestampFromS3Key should handle edge case with multiple slashes" {
            val s3Key = "0105-speed-limits/2025-01-15T10-30-00Z//snapshot.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)

            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-01-15T10:30:00Z")
        }

        "parseTimestampFromS3Key should handle microseconds precision timestamps" {
            val s3Key = "0105-speed-limits/2025-01-15T10-30-00-123456Z/snapshot.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)

            // This should fail parsing as we expect standard ISO format
            result.shouldBeNull()
        }

        "parseTimestampFromS3Key should handle timezone-aware timestamps" {
            val s3Key = "0105-speed-limits/2025-01-15T10-30-00Z/snapshot.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)

            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-01-15T10:30:00Z")
        }

        "parseTimestampFromS3Key should handle nested folder structure" {
            val s3Key = "exports/norway/0105-speed-limits/2025-01-15T10-30-00Z/snapshot.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)

            // Regex approach correctly finds timestamp regardless of folder depth
            result.shouldNotBeNull()
            result shouldBe Instant.parse("2025-01-15T10:30:00Z")
        }

        "parseTimestampFromS3Key should handle leap year dates" {
            val s3Key = "0105-speed-limits/2024-02-29T12-00-00Z/snapshot.xml.gz"
            val result = service.parseTimestampFromS3Key(s3Key)

            result.shouldNotBeNull()
            result shouldBe Instant.parse("2024-02-29T12:00:00Z")
        }
    })
