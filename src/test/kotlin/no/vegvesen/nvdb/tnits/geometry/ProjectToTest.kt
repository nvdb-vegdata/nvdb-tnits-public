package no.vegvesen.nvdb.tnits.geometry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ProjectToTest :
    StringSpec({

        "projects UTM33 to WGS84 with 6 decimals" {
            val wkt = "LINESTRING (512345 6123456, 512445 6123556)"
            val geometry = parseWkt(wkt, SRID.UTM33)

            val projected = geometry.projectTo(SRID.WGS84)

            projected.srid shouldBe SRID.WGS84
            projected.toText() shouldBe "LINESTRING (55.257432 15.194231, 55.258328 15.195809)"
        }
    })
