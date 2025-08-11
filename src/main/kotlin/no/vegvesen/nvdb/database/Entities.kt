package no.vegvesen.nvdb.database

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class RoadnetEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<RoadnetEntity>(RoadnetTable)
    
    var roadSystemReference by RoadnetTable.roadSystemReference
    var roadNumber by RoadnetTable.roadNumber
    var roadCategory by RoadnetTable.roadCategory
    var fromPosition by RoadnetTable.fromPosition
    var toPosition by RoadnetTable.toPosition
    var geometry by RoadnetTable.geometry
    var municipality by RoadnetTable.municipality
    var county by RoadnetTable.county
    var createdAt by RoadnetTable.createdAt
    var modifiedAt by RoadnetTable.modifiedAt
}

class SpeedLimitEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SpeedLimitEntity>(SpeedLimitTable)
    
    var nvdbId by SpeedLimitTable.nvdbId
    var roadSystemReference by SpeedLimitTable.roadSystemReference
    var roadNumber by SpeedLimitTable.roadNumber
    var roadCategory by SpeedLimitTable.roadCategory
    var fromPosition by SpeedLimitTable.fromPosition
    var toPosition by SpeedLimitTable.toPosition
    var speedLimit by SpeedLimitTable.speedLimit
    var geometry by SpeedLimitTable.geometry
    var openLrReference by SpeedLimitTable.openLrReference
    var validFrom by SpeedLimitTable.validFrom
    var validTo by SpeedLimitTable.validTo
    var municipality by SpeedLimitTable.municipality
    var county by SpeedLimitTable.county
    var createdAt by SpeedLimitTable.createdAt
    var modifiedAt by SpeedLimitTable.modifiedAt
    var lastSyncedAt by SpeedLimitTable.lastSyncedAt
}

@Serializable
data class SpeedLimit(
    val id: Long,
    val nvdbId: Long,
    val roadSystemReference: Int,
    val roadNumber: Int,
    val roadCategory: String,
    val fromPosition: Double,
    val toPosition: Double,
    val speedLimit: Int,
    val geometry: String,
    val openLrReference: String?,
    val validFrom: LocalDateTime?,
    val validTo: LocalDateTime?,
    val municipality: String?,
    val county: String?,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime
)

@Serializable
data class RoadnetSegment(
    val id: Long,
    val roadSystemReference: Int,
    val roadNumber: Int,
    val roadCategory: String,
    val fromPosition: Double,
    val toPosition: Double,
    val geometry: String,
    val municipality: String?,
    val county: String?,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime
)