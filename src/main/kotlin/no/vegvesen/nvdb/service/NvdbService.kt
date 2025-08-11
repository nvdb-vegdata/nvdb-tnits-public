package no.vegvesen.nvdb.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import no.vegvesen.nvdb.database.RoadnetEntity
import no.vegvesen.nvdb.database.SpeedLimitEntity
import no.vegvesen.nvdb.database.SpeedLimitTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class NvdbService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    suspend fun loadInitialRoadnet(): Int {
        // This is a placeholder implementation
        // In a real implementation, we would use the generated NVDB API client
        // to fetch roadnet data and store it in the database
        
        transaction {
            // Create sample roadnet data for demonstration
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            
            RoadnetEntity.new {
                roadSystemReference = 1
                roadNumber = 22
                roadCategory = "E"
                fromPosition = 0.0
                toPosition = 1000.0
                geometry = "LINESTRING(10.0 59.0, 10.1 59.1)"
                municipality = "Oslo"
                county = "Viken"
                createdAt = now
                modifiedAt = now
            }
        }
        
        return 1 // Number of roadnet segments loaded
    }

    suspend fun loadInitialSpeedLimits(): Int {
        // This is a placeholder implementation
        // In a real implementation, we would use the generated NVDB API client
        // to fetch speed limit data (objekttype 105) and store it in the database
        
        transaction {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            
            SpeedLimitEntity.new {
                nvdbId = 12345L
                roadSystemReference = 1
                roadNumber = 22
                roadCategory = "E"
                fromPosition = 0.0
                toPosition = 1000.0
                speedLimit = 80
                geometry = "LINESTRING(10.0 59.0, 10.1 59.1)"
                openLrReference = null // Will be calculated later
                validFrom = now
                validTo = null
                municipality = "Oslo"
                county = "Viken"
                createdAt = now
                modifiedAt = now
                lastSyncedAt = now
            }
        }
        
        return 1 // Number of speed limits loaded
    }

    suspend fun findChangedSpeedLimits(since: LocalDateTime): List<SpeedLimitEntity> {
        return transaction {
            SpeedLimitEntity.find { 
                SpeedLimitTable.modifiedAt greater since 
            }.toList()
        }
    }

    fun close() {
        client.close()
    }
}