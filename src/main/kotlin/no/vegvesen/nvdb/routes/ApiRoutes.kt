package no.vegvesen.nvdb.routes

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.vegvesen.nvdb.OpenLR
import no.vegvesen.nvdb.Segment
import no.vegvesen.nvdb.SpeedLimitFeature
import no.vegvesen.nvdb.buildOneSpeedLimitFeature
import org.openapitools.client.ApiClient

fun Route.apiRoutes() {
    val apiClient = ApiClient()
    apiClient.updateBaseUri("https://nvdbapiles.atlas.vegvese.no/api/v4")

    route("/api") {
        get("/speed-limit") {
            val speedLimit =
                SpeedLimitFeature(
                    docLocalId = "123",
                    stableId = "1234",
                    operation = "modify",
                    kmh = 10,
                    openLR = OpenLR(base64 = "base64string"),
                    geometry =
                        listOf(
                            Segment(lon = 10.0, lat = 59.0),
                            Segment(lon = 11.0, lat = 60.0),
                        ),
                    linearHref = "linearHrefExample",
                )
            call.respondText(
                buildOneSpeedLimitFeature(speedLimit),
                ContentType.Application.Xml,
            )
        }
    }
}
