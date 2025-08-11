package no.vegvesen.nvdb.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.Serializable
import no.vegvesen.nvdb.service.NvdbService
import no.vegvesen.nvdb.service.TnItsService
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class LoadResponse(
    val message: String,
    val roadnetCount: Int,
    val speedLimitCount: Int,
    val timestamp: String,
)

@OptIn(ExperimentalTime::class)
fun Route.apiRoutes() {
    val nvdbService = NvdbService()
    val tnItsService = TnItsService()

    route("/api") {
        post("/initial-load") {
            val roadnetCount = nvdbService.loadInitialRoadnet()
            val speedLimitCount = nvdbService.loadInitialSpeedLimits()

            call.respond(
                LoadResponse(
                    message = "Initial load completed successfully",
                    roadnetCount = roadnetCount,
                    speedLimitCount = speedLimitCount,
                    timestamp = Clock.System.now().toString(),
                ),
            )
        }

        get("/snapshot/full") {
            val snapshot = tnItsService.generateFullSnapshot()
            call.respond(snapshot)
        }

        get("/snapshot/daily/{date}") {
            val dateString = call.parameters["date"]
            if (dateString == null) {
                call.respond(mapOf("error" to "Date parameter is required"))
                return@get
            }

            try {
                val date = LocalDate.parse(dateString)
                val kotlinxInstant = date.atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
                val instant = kotlin.time.Instant.fromEpochMilliseconds(kotlinxInstant.toEpochMilliseconds())
                val snapshot = tnItsService.generateIncrementalSnapshot(instant)
                call.respond(snapshot)
            } catch (e: Exception) {
                call.respond(mapOf("error" to "Invalid date format. Use YYYY-MM-DD"))
            }
        }
    }
}
