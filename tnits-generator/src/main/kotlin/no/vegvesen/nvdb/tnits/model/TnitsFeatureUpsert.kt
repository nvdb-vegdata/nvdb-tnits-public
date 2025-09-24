package no.vegvesen.nvdb.tnits.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.UpdateType
import no.vegvesen.nvdb.tnits.hash.getHash
import org.locationtech.jts.geom.Geometry
import org.openlr.locationreference.LineLocationReference
import kotlin.time.Instant

sealed interface TnitsFeature {
    val id: Long
    val type: ExportedFeatureType
    val updateType: UpdateType
}

data class TnitsFeatureRemoved(override val id: Long, override val type: ExportedFeatureType) : TnitsFeature {
    override val updateType: UpdateType = UpdateType.Remove
}

@Serializable
data class TnitsFeatureUpsert(
    override val id: Long,
    override val type: ExportedFeatureType,
    @Serializable(with = JtsGeometrySerializer::class)
    val geometry: Geometry,
    val properties: Map<RoadFeaturePropertyType, RoadFeatureProperty>,
    val openLrLocationReferences: List<LineLocationReference>,
    val nvdbLocationReferences: List<VegobjektStedfesting>,
    val validFrom: LocalDate,
    val validTo: LocalDate? = null,
    override val updateType: UpdateType,
    val beginLifespanVersion: Instant,
) : TnitsFeature {
    val hash by lazy {
        ProtoBuf.encodeToByteArray(serializer(), this).getHash()
    }
}

// Makes it possible to have more complex property types, like ConditionSet, that handle their own XML serialization
sealed interface RoadFeatureProperty

data class IntProperty(val value: Int) : RoadFeatureProperty
