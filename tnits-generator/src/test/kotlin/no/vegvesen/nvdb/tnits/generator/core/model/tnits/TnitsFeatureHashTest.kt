package no.vegvesen.nvdb.tnits.generator.core.model.tnits

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID
import org.locationtech.jts.geom.GeometryFactory
import kotlin.time.Instant

class TnitsFeatureHashTest : ShouldSpec({

    should("produce identical hash for features with same data but different updateType") {
        val geometryFactory = GeometryFactory()
        val lineString = geometryFactory.createLineString(
            arrayOf(
                org.locationtech.jts.geom.Coordinate(0.0, 0.0),
                org.locationtech.jts.geom.Coordinate(10.0, 10.0),
            ),
        )
        lineString.srid = SRID.UTM33

        val baselineTimestamp = Instant.parse("2025-01-15T10:30:00Z")
        val sharedDate = LocalDate(2025, 1, 15)

        val template = TnitsFeature(
            id = 123L,
            type = ExportedFeatureType.SpeedLimit,
            geometry = lineString,
            properties = mapOf(RoadFeaturePropertyType.MaximumSpeedLimit to IntProperty(80)),
            openLrLocationReferences = listOf("openLr1"),
            nvdbLocationReferences = emptyList(),
            validFrom = sharedDate,
            validTo = null,
            updateType = UpdateType.Snapshot,
            beginLifespanVersion = baselineTimestamp,
        )

        val featureWithAdd = template.copy(updateType = UpdateType.Add)
        val featureWithModify = template.copy(updateType = UpdateType.Modify)

        template.hash shouldBe featureWithAdd.hash
        template.hash shouldBe featureWithModify.hash
        featureWithAdd.hash shouldBe featureWithModify.hash
    }

    should("produce different hash for features with different properties") {
        val geometryFactory = GeometryFactory()
        val lineString = geometryFactory.createLineString(
            arrayOf(
                org.locationtech.jts.geom.Coordinate(0.0, 0.0),
                org.locationtech.jts.geom.Coordinate(10.0, 10.0),
            ),
        )
        lineString.srid = SRID.UTM33

        val baselineTimestamp = Instant.parse("2025-01-15T10:30:00Z")
        val sharedDate = LocalDate(2025, 1, 15)

        val template = TnitsFeature(
            id = 123L,
            type = ExportedFeatureType.SpeedLimit,
            geometry = lineString,
            properties = mapOf(RoadFeaturePropertyType.MaximumSpeedLimit to IntProperty(80)),
            openLrLocationReferences = listOf("openLr1"),
            nvdbLocationReferences = emptyList(),
            validFrom = sharedDate,
            validTo = null,
            updateType = UpdateType.Snapshot,
            beginLifespanVersion = baselineTimestamp,
        )

        val featureWithDifferentSpeed = template.copy(
            properties = mapOf(RoadFeaturePropertyType.MaximumSpeedLimit to IntProperty(100)),
        )

        template.hash shouldBe template.hash
        template.hash != featureWithDifferentSpeed.hash
    }
})
