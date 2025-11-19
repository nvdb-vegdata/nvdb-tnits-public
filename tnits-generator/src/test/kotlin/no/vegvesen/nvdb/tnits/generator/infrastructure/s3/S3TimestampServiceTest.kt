package no.vegvesen.nvdb.tnits.generator.infrastructure.s3

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.vegvesen.nvdb.tnits.common.utils.parseTimestampFromS3Key
import kotlin.time.Instant

class S3TimestampServiceTest : ShouldSpec({
    should("parse valid snapshot key from S3") {
        val s3Key = "0105-speed-limits/2025-01-15T10-30-00Z/snapshot.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)

        result.shouldNotBeNull()
        result shouldBe Instant.parse("2025-01-15T10:30:00Z")
    }

    should("parse valid update key from S3") {
        val s3Key = "0105-speed-limits/2025-01-15T14-45-30Z/update.xml"
        val result = parseTimestampFromS3Key(s3Key)

        result.shouldNotBeNull()
        result shouldBe Instant.parse("2025-01-15T14:45:30Z")
    }

    should("handle uncompressed files when parsing S3 key") {
        val s3Key = "0105-speed-limits/2025-12-31T23-59-59Z/snapshot.xml"
        val result = parseTimestampFromS3Key(s3Key)

        result.shouldNotBeNull()
        result shouldBe Instant.parse("2025-12-31T23:59:59Z")
    }

    should("handle different vegobjekttype padding in S3 key") {
        val s3Key = "0001-speed-limits/2025-06-15T12-00-00Z/update.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)

        result.shouldNotBeNull()
        result shouldBe Instant.parse("2025-06-15T12:00:00Z")
    }

    should("return null for blank S3 key") {
        val result = parseTimestampFromS3Key("")
        result.shouldBeNull()
    }

    should("return null for S3 key with insufficient parts") {
        val s3Key = "0105-speed-limits/snapshot.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)
        result.shouldBeNull()
    }

    should("return null for S3 key with only one part") {
        val s3Key = "0105-speed-limits"
        val result = parseTimestampFromS3Key(s3Key)
        result.shouldBeNull()
    }

    should("return null for invalid timestamp format in S3 key") {
        val s3Key = "0105-speed-limits/not-a-timestamp/snapshot.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)
        result.shouldBeNull()
    }

    should("return null for malformed timestamp in S3 key") {
        val s3Key = "0105-speed-limits/2025-13-40T25-70-80Z/snapshot.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)
        result.shouldBeNull()
    }

    should("return null for timestamp without T separator in S3 key") {
        val s3Key = "0105-speed-limits/2025-01-15-10-30-00Z/snapshot.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)
        result.shouldBeNull()
    }

    should("return null for empty timestamp part in S3 key") {
        val s3Key = "0105-speed-limits//snapshot.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)
        result.shouldBeNull()
    }

    should("handle edge case with multiple slashes in S3 key") {
        val s3Key = "0105-speed-limits/2025-01-15T10-30-00Z//snapshot.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)

        result.shouldNotBeNull()
        result shouldBe Instant.parse("2025-01-15T10:30:00Z")
    }

    should("handle microseconds precision timestamps in S3 key") {
        val s3Key = "0105-speed-limits/2025-01-15T10-30-00-123456Z/snapshot.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)

        // This should fail parsing as we expect standard ISO format
        result.shouldBeNull()
    }

    should("handle timezone-aware timestamps in S3 key") {
        val s3Key = "0105-speed-limits/2025-01-15T10-30-00Z/snapshot.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)

        result.shouldNotBeNull()
        result shouldBe Instant.parse("2025-01-15T10:30:00Z")
    }

    should("handle nested folder structure in S3 key") {
        val s3Key = "exports/norway/0105-speed-limits/2025-01-15T10-30-00Z/snapshot.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)

        // Regex approach correctly finds timestamp regardless of folder depth
        result.shouldNotBeNull()
        result shouldBe Instant.parse("2025-01-15T10:30:00Z")
    }

    should("handle leap year dates in S3 key") {
        val s3Key = "0105-speed-limits/2024-02-29T12-00-00Z/snapshot.xml.gz"
        val result = parseTimestampFromS3Key(s3Key)

        result.shouldNotBeNull()
        result shouldBe Instant.parse("2024-02-29T12:00:00Z")
    }
})
