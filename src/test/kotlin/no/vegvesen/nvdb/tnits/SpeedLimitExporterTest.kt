package no.vegvesen.nvdb.tnits

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.time.Instant

class SpeedLimitExporterTest :
    StringSpec({

        "generateS3Key should format with padded vegobjekttype for speed limits" {
            // Arrange
            val timestamp = Instant.parse("2025-01-15T10:30:00Z")

            // Act & Assert
            val snapshotKey = SpeedLimitExporter.generateS3Key(timestamp, SpeedLimitExporter.ExportType.Snapshot, false, 105)
            val updateKey = SpeedLimitExporter.generateS3Key(timestamp, SpeedLimitExporter.ExportType.Update, false, 105)

            snapshotKey shouldBe "0105-speed-limits/2025-01-15T10-30-00Z/snapshot.xml"
            updateKey shouldBe "0105-speed-limits/2025-01-15T10-30-00Z/update.xml"
        }

        "generateS3Key should include .gz extension when gzip is enabled" {
            // Arrange
            val timestamp = Instant.parse("2025-01-15T10:30:00Z")

            // Act
            val key = SpeedLimitExporter.generateS3Key(timestamp, SpeedLimitExporter.ExportType.Snapshot, true, 105)

            // Assert
            key shouldBe "0105-speed-limits/2025-01-15T10-30-00Z/snapshot.xml.gz"
        }

        "generateS3Key should work with different vegobjekttypes" {
            // Arrange
            val timestamp = Instant.parse("2025-01-15T10:30:00Z")

            // Act & Assert
            val barrierKey = SpeedLimitExporter.generateS3Key(timestamp, SpeedLimitExporter.ExportType.Snapshot, false, 1)
            val trafficSignKey = SpeedLimitExporter.generateS3Key(timestamp, SpeedLimitExporter.ExportType.Snapshot, false, 95)
            val speedLimitKey = SpeedLimitExporter.generateS3Key(timestamp, SpeedLimitExporter.ExportType.Snapshot, false, 105)

            barrierKey shouldStartWith "0001-speed-limits/"
            trafficSignKey shouldStartWith "0095-speed-limits/"
            speedLimitKey shouldStartWith "0105-speed-limits/"
        }

        "generateS3Key should replace colons in timestamp with hyphens" {
            // Arrange
            val timestamp = Instant.parse("2025-01-15T10:30:45.123Z")

            // Act
            val key = SpeedLimitExporter.generateS3Key(timestamp, SpeedLimitExporter.ExportType.Snapshot, false, 105)

            // Assert
            key shouldContain "2025-01-15T10-30-45Z"
            key shouldBe "0105-speed-limits/2025-01-15T10-30-45Z/snapshot.xml"
        }
    })
