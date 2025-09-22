package no.vegvesen.nvdb.tnits

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.vegvesen.nvdb.tnits.model.ExportedFeatureType
import kotlin.time.Instant

class SpeedLimitExporterTest :
    StringSpec({

        "generateS3Key should format with padded vegobjekttype for speed limits" {
            // Arrange
            val timestamp = Instant.parse("2025-01-15T10:30:00Z")

            // Act & Assert
            val snapshotKey = TnitsFeatureExporter.generateS3Key(timestamp, TnitsFeatureExporter.ExportType.Snapshot, false, ExportedFeatureType.SpeedLimit)
            val updateKey = TnitsFeatureExporter.generateS3Key(timestamp, TnitsFeatureExporter.ExportType.Update, false, ExportedFeatureType.SpeedLimit)

            snapshotKey shouldBe "0105-speedLimit/2025-01-15T10-30-00Z/snapshot.xml"
            updateKey shouldBe "0105-speedLimit/2025-01-15T10-30-00Z/update.xml"
        }

        "generateS3Key should include .gz extension when gzip is enabled" {
            // Arrange
            val timestamp = Instant.parse("2025-01-15T10:30:00Z")

            // Act
            val key = TnitsFeatureExporter.generateS3Key(timestamp, TnitsFeatureExporter.ExportType.Snapshot, true, ExportedFeatureType.SpeedLimit)

            // Assert
            key shouldBe "0105-speedLimit/2025-01-15T10-30-00Z/snapshot.xml.gz"
        }

        "generateS3Key should replace colons in timestamp with hyphens" {
            // Arrange
            val timestamp = Instant.parse("2025-01-15T10:30:45.123Z")

            // Act
            val key = TnitsFeatureExporter.generateS3Key(timestamp, TnitsFeatureExporter.ExportType.Snapshot, false, ExportedFeatureType.SpeedLimit)

            // Assert
            key shouldContain "2025-01-15T10-30-45Z"
            key shouldBe "0105-speedLimit/2025-01-15T10-30-45Z/snapshot.xml"
        }
    })
