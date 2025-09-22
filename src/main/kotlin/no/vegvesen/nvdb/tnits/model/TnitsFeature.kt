package no.vegvesen.nvdb.tnits.model

import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.tnits.UpdateType
import org.locationtech.jts.geom.Geometry
import org.openlr.locationreference.LineLocationReference
import kotlin.time.Instant

data class TnitsFeature(
    val id: Long,
    val type: ExportedFeatureType,
    val geometry: Geometry,
    val properties: Map<RoadFeaturePropertyType, RoadFeatureProperty>,
    val openLrLocationReferences: List<LineLocationReference>,
    val nvdbLocationReferences: List<VegobjektStedfesting>,
    val validFrom: LocalDate,
    val validTo: LocalDate? = null,
    val updateType: UpdateType,
    val beginLifespanVersion: Instant,
)

// Makes it possible to have more complex property types, like ConditionSet, that handle their own XML serialization
sealed interface RoadFeatureProperty

data class IntProperty(val value: Int) : RoadFeatureProperty
