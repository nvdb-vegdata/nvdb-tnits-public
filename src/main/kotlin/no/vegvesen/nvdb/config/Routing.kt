package no.vegvesen.nvdb.config

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorredoc.redoc
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.vegvesen.nvdb.routes.apiRoutes
import no.vegvesen.nvdb.routes.healthRoutes

fun Application.configureRouting() {
    install(OpenApi) {
        info {
            title = "NVDB TN-ITS Prototype API"
            version = "1.0.0"
            description = "API for exporting TN-ITS daily changes for speed limits from the Norwegian Road Database (NVDB)"
        }
        server {
            url = "http://localhost:8080"
            description = "Development Server"
        }
    }

    routing {
        healthRoutes()
        apiRoutes()

        // Serve OpenAPI specification at /api.json
        route("api.json") {
            openApi()
        }

        // Serve Swagger UI at /swagger
        route("swagger") {
            swaggerUI("/api.json")
        }

        // Serve ReDoc at /redoc
        route("redoc") {
            redoc("/api.json")
        }
    }
}
