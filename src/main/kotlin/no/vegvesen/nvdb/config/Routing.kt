package no.vegvesen.nvdb.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.vegvesen.nvdb.routes.apiRoutes
import no.vegvesen.nvdb.routes.healthRoutes

fun Application.configureRouting() {
    routing {
        healthRoutes()
        apiRoutes()
    }
}