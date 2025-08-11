package no.vegvesen.nvdb.routes

import io.ktor.server.routing.*

fun Route.apiRoutes() {
    route("/api") {
        post("/initial-load") {
            TODO()
        }

        get("/snapshot/full") {
            TODO()
        }

        get("/snapshot/daily/{date}") {
            TODO()
        }
    }
}
