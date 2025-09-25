package no.vegvesen.nvdb.tnits.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.UpdateType
import no.vegvesen.nvdb.tnits.hash.getHash
import org.locationtech.jts.geom.Geometry
import kotlin.time.Instant

@Serializable
sealed interface TnitsFeature {
    val id: Long
    val type: ExportedFeatureType
    val updateType: UpdateType

    @Transient
    val hash: Long
}

@Serializable
data class TnitsFeatureRemoved(override val id: Long, override val type: ExportedFeatureType) : TnitsFeature {
    override val updateType: UpdateType = UpdateType.Remove

    override val hash by lazy {
        ProtoBuf.encodeToByteArray(serializer(), this).getHash()
    }
}

@Serializable
data class TnitsFeatureUpsert(
    override val id: Long,
    override val type: ExportedFeatureType,
    @Serializable(with = JtsGeometrySerializer::class)
    val geometry: Geometry,
    val properties: Map<RoadFeaturePropertyType, RoadFeatureProperty>,
    val openLrLocationReferences: List<String>,
    val nvdbLocationReferences: List<VegobjektStedfesting>,
    val validFrom: LocalDate,
    val validTo: LocalDate? = null,
    override val updateType: UpdateType,
    val beginLifespanVersion: Instant,
) : TnitsFeature {
    override val hash by lazy {
        ProtoBuf.encodeToByteArray(serializer(), this).getHash()
    }
}

@Serializable
// Makes it possible to have more complex property types, like ConditionSet, that handle their own XML serialization
sealed interface RoadFeatureProperty

@Serializable
data class IntProperty(val value: Int) : RoadFeatureProperty
