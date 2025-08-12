package no.vegvesen.nvdb.routes

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.openapitools.client.ApiClient

fun Route.apiRoutes() {
    val apiClient = ApiClient()
    apiClient.updateBaseUri("https://nvdbapiles.atlas.vegvese.no/api/v4")

    route("/api") {
        post("/initial-load", {
            description = "Load initial roadnet and speed limit data from NVDB"
            summary = "Loads initial data"
        }) {
            call.respond(
                HttpStatusCode.NotImplemented,
                mapOf("message" to "Initial load endpoint not yet implemented"),
            )
        }

        get("/snapshot/full", {
            description = "Generate complete TN-ITS speed limit snapshot"
            summary = "Generate full snapshot"
            tags = listOf("Snapshots")
        }) {
            call.respond(
                HttpStatusCode.NotImplemented,
                mapOf("message" to "Full snapshot endpoint not yet implemented"),
            )
        }

        get("/snapshot/daily/{date}", {
            description = "Generate incremental changes since specified date"
            summary = "Generate daily snapshot"
            tags = listOf("Snapshots")
            request {
                pathParameter<String>("date") {
                    description = "Date since when to generate changes (YYYY-MM-DD format)"
                }
            }
        }) {
            call.respond(
                HttpStatusCode.NotImplemented,
                mapOf("message" to "Daily snapshot endpoint not yet implemented"),
            )
        }
    }
}
