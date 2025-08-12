package no.vegvesen.nvdb.routes

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String,
)

fun Route.healthRoutes() {
    get("/health", {
        description = "Health check endpoint"
        summary = "Returns the health status of the service"
        tags = listOf("Health")
        response {
            HttpStatusCode.OK to {
                description = "Service is healthy"
                body<HealthResponse> {
                    description = "Health status response"
                }
            }
        }
    }) {
        call.respond(
            HealthResponse(
                "OK",
                Clock.System
                    .now()
                    .toString(),
            ),
        )
    }
}
