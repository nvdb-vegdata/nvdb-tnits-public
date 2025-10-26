package no.vegvesen.nvdb.tnits.generator.core.model.tnits

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.model.JtsGeometrySerializer
import no.vegvesen.nvdb.tnits.generator.core.model.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.generator.core.services.hash.getHash
import org.locationtech.jts.geom.Geometry
import kotlin.time.Instant

@Serializable
data class TnitsFeature(
    val id: Long,
    val type: ExportedFeatureType,
    @Serializable(with = JtsGeometrySerializer::class)
    val geometry: Geometry?,
    val properties: Map<RoadFeaturePropertyType, RoadFeatureProperty>,
    val openLrLocationReferences: List<String>,
    val nvdbLocationReferences: List<VegobjektStedfesting>,
    val validFrom: LocalDate,
    val validTo: LocalDate? = null,
    @Transient
    val updateType: UpdateType = UpdateType.Snapshot,
    val beginLifespanVersion: Instant,
) {
    val hash by lazy {
        ProtoBuf.encodeToByteArray(serializer(), this).getHash()
    }
}

@Serializable
// Makes it possible to have more complex property types, like ConditionSet, that handle their own XML serialization
sealed interface RoadFeatureProperty

@Serializable
@SerialName("IntProperty")
data class IntProperty(val value: Int) : RoadFeatureProperty

@Serializable
@SerialName("StringProperty")
data class StringProperty(val value: String) : RoadFeatureProperty
