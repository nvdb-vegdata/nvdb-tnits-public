package no.vegvesen.nvdb.config

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.vegvesen.nvdb.routes.apiRoutes
import no.vegvesen.nvdb.routes.healthRoutes

fun Application.configureRouting() {
    install(OpenApi) {
        info {
            title = "NVDB TN-ITS Prototype API"
            version = "1.0.0"
            description =
                "API for exporting TN-ITS daily changes for speed limits from the Norwegian Road Database (NVDB)"
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

        route("swagger") {
            swaggerUI("/api.json")
        }

        // Redirect root to Swagger UI
        get("/", {
            hidden = true
        }) {
            call.respondRedirect("/swagger", permanent = true)
        }
    }
}
