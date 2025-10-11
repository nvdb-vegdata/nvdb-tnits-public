package no.vegvesen.nvdb.tnits.generator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsExportType
import no.vegvesen.nvdb.tnits.generator.infrastructure.s3.TnitsFeatureS3Exporter.Companion.generateS3Key
import kotlin.time.Instant

class GenerateS3KeyTest : ShouldSpec({

    should("format S3 key with padded vegobjekttype for speed limits") {
        // Arrange
        val timestamp = Instant.parse("2025-01-15T10:30:00Z")

        // Act & Assert
        val snapshotKey = generateS3Key(timestamp, TnitsExportType.Snapshot, false, ExportedFeatureType.SpeedLimit)
        val updateKey = generateS3Key(timestamp, TnitsExportType.Update, false, ExportedFeatureType.SpeedLimit)

        snapshotKey shouldBe "0105-speedLimit/2025-01-15T10-30-00Z/snapshot.xml"
        updateKey shouldBe "0105-speedLimit/2025-01-15T10-30-00Z/update.xml"
    }

    should("include .gz extension in S3 key when gzip is enabled") {
        // Arrange
        val timestamp = Instant.parse("2025-01-15T10:30:00Z")

        // Act
        val key = generateS3Key(timestamp, TnitsExportType.Snapshot, true, ExportedFeatureType.SpeedLimit)

        // Assert
        key shouldBe "0105-speedLimit/2025-01-15T10-30-00Z/snapshot.xml.gz"
    }

    should("replace colons in timestamp with hyphens in S3 key") {
        // Arrange
        val timestamp = Instant.parse("2025-01-15T10:30:45.123Z")

        // Act
        val key = generateS3Key(timestamp, TnitsExportType.Snapshot, false, ExportedFeatureType.SpeedLimit)

        // Assert
        key shouldContain "2025-01-15T10-30-45Z"
        key shouldBe "0105-speedLimit/2025-01-15T10-30-45Z/snapshot.xml"
    }
})
