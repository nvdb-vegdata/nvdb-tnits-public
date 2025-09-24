package no.vegvesen.nvdb.tnits.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.UpdateType
import no.vegvesen.nvdb.tnits.hash.getHash
import org.locationtech.jts.geom.Geometry
import org.openlr.locationreference.LineLocationReference
import kotlin.time.Instant

sealed interface TnitsFeature

data class TnitsFeatureRemoved(val id: Long, val type: ExportedFeatureType) : TnitsFeature

@Serializable
data class TnitsFeatureUpsert(
    val id: Long,
    val type: ExportedFeatureType,
    @Serializable(with = JtsGeometrySerializer::class)
    val geometry: Geometry,
    val properties: Map<RoadFeaturePropertyType, RoadFeatureProperty>,
    val openLrLocationReferences: List<LineLocationReference>,
    val nvdbLocationReferences: List<VegobjektStedfesting>,
    val validFrom: LocalDate,
    val validTo: LocalDate? = null,
    val updateType: UpdateType,
    val beginLifespanVersion: Instant,
) : TnitsFeature {
    @OptIn(ExperimentalSerializationApi::class)
    val hash by lazy {
        ProtoBuf.encodeToByteArray(serializer(), this).getHash()
    }
}

// Makes it possible to have more complex property types, like ConditionSet, that handle their own XML serialization
sealed interface RoadFeatureProperty

data class IntProperty(val value: Int) : RoadFeatureProperty
