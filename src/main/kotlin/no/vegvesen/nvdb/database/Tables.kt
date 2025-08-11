package no.vegvesen.nvdb.database

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object RoadnetTable : LongIdTable("roadnet") {
    val roadSystemReference = integer("road_system_reference")
    val roadNumber = integer("road_number")
    val roadCategory = varchar("road_category", 10)
    val fromPosition = double("from_position")
    val toPosition = double("to_position")
    val geometry = text("geometry") // WKT format
    val municipality = varchar("municipality", 100).nullable()
    val county = varchar("county", 100).nullable()
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
}

object SpeedLimitTable : LongIdTable("speed_limits") {
    val nvdbId = long("nvdb_id").uniqueIndex()
    val roadSystemReference = integer("road_system_reference")
    val roadNumber = integer("road_number") 
    val roadCategory = varchar("road_category", 10)
    val fromPosition = double("from_position")
    val toPosition = double("to_position")
    val speedLimit = integer("speed_limit")
    val geometry = text("geometry") // WKT format
    val openLrReference = text("openlr_reference").nullable() // OpenLR encoded location reference
    val validFrom = datetime("valid_from").nullable()
    val validTo = datetime("valid_to").nullable()
    val municipality = varchar("municipality", 100).nullable()
    val county = varchar("county", 100).nullable()
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val lastSyncedAt = datetime("last_synced_at").nullable()
}