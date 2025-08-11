package no.vegvesen.nvdb.service

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import no.vegvesen.nvdb.database.SpeedLimit
import no.vegvesen.nvdb.database.SpeedLimitEntity
import no.vegvesen.nvdb.database.SpeedLimitTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class TnItsSpeedLimit(
    val id: String,
    val speedLimit: Int,
    val locationReference: String, // OpenLR encoded
    val validFrom: String?,
    val validTo: String?,
    val lastUpdated: String
)

@Serializable
data class TnItsSnapshot(
    val timestamp: String,
    val type: String, // "FULL" or "INCREMENTAL"
    val baseDate: String?,
    val speedLimits: List<TnItsSpeedLimit>
)

class TnItsService {
    
    fun generateFullSnapshot(): TnItsSnapshot {
        val speedLimits = transaction {
            SpeedLimitTable.selectAll().map { row: ResultRow ->
                TnItsSpeedLimit(
                    id = "NVDB_${row[SpeedLimitTable.nvdbId]}",
                    speedLimit = row[SpeedLimitTable.speedLimit],
                    locationReference = row[SpeedLimitTable.openLrReference] ?: generateOpenLrReference(row[SpeedLimitTable.geometry]),
                    validFrom = row[SpeedLimitTable.validFrom]?.toString(),
                    validTo = row[SpeedLimitTable.validTo]?.toString(),
                    lastUpdated = row[SpeedLimitTable.modifiedAt].toString()
                )
            }
        }
        
        return TnItsSnapshot(
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            type = "FULL",
            baseDate = null,
            speedLimits = speedLimits
        )
    }
    
    fun generateIncrementalSnapshot(since: LocalDateTime): TnItsSnapshot {
        val speedLimits = transaction {
            SpeedLimitEntity.find { 
                SpeedLimitTable.modifiedAt greater since 
            }.map { entity: SpeedLimitEntity ->
                TnItsSpeedLimit(
                    id = "NVDB_${entity.nvdbId}",
                    speedLimit = entity.speedLimit,
                    locationReference = entity.openLrReference ?: generateOpenLrReference(entity.geometry),
                    validFrom = entity.validFrom?.toString(),
                    validTo = entity.validTo?.toString(),
                    lastUpdated = entity.modifiedAt.toString()
                )
            }
        }
        
        return TnItsSnapshot(
            timestamp = kotlinx.datetime.Clock.System.now().toString(),
            type = "INCREMENTAL",
            baseDate = since.toString(),
            speedLimits = speedLimits
        )
    }
    
    private fun generateOpenLrReference(geometry: String): String {
        // Placeholder for OpenLR encoding
        // In a real implementation, this would use an OpenLR library
        // to convert WKT geometry to OpenLR binary representation
        return "OpenLR_placeholder_${geometry.hashCode()}"
    }
}