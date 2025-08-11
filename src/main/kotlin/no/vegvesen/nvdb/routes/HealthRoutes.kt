package no.vegvesen.nvdb.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String,
)

fun Route.healthRoutes() {
    get("/health") {
        call.respond(
            HealthResponse(
                "OK",
                kotlinx.datetime.Clock.System
                    .now()
                    .toString(),
            ),
        )
    }
}
