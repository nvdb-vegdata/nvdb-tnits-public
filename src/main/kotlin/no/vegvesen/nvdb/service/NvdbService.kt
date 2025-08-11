package no.vegvesen.nvdb.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import no.vegvesen.nvdb.database.RoadnetTable
import no.vegvesen.nvdb.database.SpeedLimit
import no.vegvesen.nvdb.database.SpeedLimitTable
import no.vegvesen.nvdb.database.SpeedLimitTable.nvdbId
import no.vegvesen.nvdb.database.SpeedLimitTable.openLrReference
import no.vegvesen.nvdb.database.SpeedLimitTable.speedLimit
import no.vegvesen.nvdb.database.SpeedLimitTable.validFrom
import no.vegvesen.nvdb.database.SpeedLimitTable.validTo
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class NvdbService {
    private val client =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }

    suspend fun loadInitialRoadnet(): Int {
        // This is a placeholder implementation
        // In a real implementation, we would use the generated NVDB API client
        // to fetch roadnet data and store it in the database

        return transaction {
            val now = Clock.System.now()

            RoadnetTable.insert {
                it[roadSystemReference] = 1
                it[roadNumber] = 22
                it[roadCategory] = "E"
                it[fromPosition] = 0.0
                it[toPosition] = 1000.0
                it[geometry] = "LINESTRING(10.0 59.0, 10.1 59.1)"
                it[municipality] = "Oslo"
                it[county] = "Viken"
                it[createdAt] = now
                it[modifiedAt] = now
            }

            1 // Number of roadnet segments loaded
        }
    }

    suspend fun loadInitialSpeedLimits(): Int {
        // This is a placeholder implementation
        // In a real implementation, we would use the generated NVDB API client
        // to fetch speed limit data (objekttype 105) and store it in the database

        return transaction {
            val now = Clock.System.now()

            SpeedLimitTable.insert {
                it[nvdbId] = 12345L
                it[roadSystemReference] = 1
                it[roadNumber] = 22
                it[roadCategory] = "E"
                it[fromPosition] = 0.0
                it[toPosition] = 1000.0
                it[speedLimit] = 80
                it[geometry] = "LINESTRING(10.0 59.0, 10.1 59.1)"
                it[openLrReference] = null
                it[validFrom] = now
                it[validTo] = null
                it[municipality] = "Oslo"
                it[county] = "Viken"
                it[createdAt] = now
                it[modifiedAt] = now
                it[lastSyncedAt] = now
            }

            1 // Number of speed limits loaded
        }
    }

    suspend fun findChangedSpeedLimits(since: Instant): List<SpeedLimit> =
        transaction {
            SpeedLimitTable
                .selectAll()
                .where { SpeedLimitTable.modifiedAt greater since }
                .map { row ->
                    SpeedLimit(
                        id = row[SpeedLimitTable.id],
                        nvdbId = row[nvdbId],
                        roadSystemReference = row[SpeedLimitTable.roadSystemReference],
                        roadNumber = row[SpeedLimitTable.roadNumber],
                        roadCategory = row[SpeedLimitTable.roadCategory],
                        fromPosition = row[SpeedLimitTable.fromPosition],
                        toPosition = row[SpeedLimitTable.toPosition],
                        speedLimit = row[speedLimit],
                        geometry = row[SpeedLimitTable.geometry],
                        openLrReference = row[openLrReference],
                        validFrom = row[validFrom],
                        validTo = row[validTo],
                        municipality = row[SpeedLimitTable.municipality],
                        county = row[SpeedLimitTable.county],
                        createdAt = row[SpeedLimitTable.createdAt],
                        modifiedAt = row[SpeedLimitTable.modifiedAt],
                    )
                }
        }

    fun close() {
        client.close()
    }
}
