package no.vegvesen.nvdb.database

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object RoadnetTable : Table("roadnet") {
    val id = long("id").autoIncrement()
    val roadSystemReference = integer("road_system_reference")
    val roadNumber = integer("road_number")
    val roadCategory = varchar("road_category", 10)
    val fromPosition = double("from_position")
    val toPosition = double("to_position")
    val geometry = text("geometry") // WKT format
    val municipality = varchar("municipality", 100).nullable()
    val county = varchar("county", 100).nullable()
    val createdAt = timestamp("created_at")
    val modifiedAt = timestamp("modified_at")

    override val primaryKey = PrimaryKey(id)
}

@OptIn(ExperimentalTime::class)
object SpeedLimitTable : Table("speed_limits") {
    val id = long("id").autoIncrement()
    val nvdbId = long("nvdb_id").uniqueIndex()
    val roadSystemReference = integer("road_system_reference")
    val roadNumber = integer("road_number")
    val roadCategory = varchar("road_category", 10)
    val fromPosition = double("from_position")
    val toPosition = double("to_position")
    val speedLimit = integer("speed_limit")
    val geometry = text("geometry") // WKT format
    val openLrReference = text("openlr_reference").nullable() // OpenLR encoded location reference
    val validFrom = timestamp("valid_from").nullable()
    val validTo = timestamp("valid_to").nullable()
    val municipality = varchar("municipality", 100).nullable()
    val county = varchar("county", 100).nullable()
    val createdAt = timestamp("created_at")
    val modifiedAt = timestamp("modified_at")
    val lastSyncedAt = timestamp("last_synced_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
