package no.vegvesen.nvdb.tnits.generator.geometry

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class GeometryHelpersTest :
    ShouldSpec({

        should("project UTM33 to WGS84 with 6 decimals") {
            val wkt = "LINESTRING (512345 6123456, 512445 6123556)"
            val geometry = parseWkt(wkt, SRID.UTM33)

            val projected = geometry.projectTo(SRID.WGS84)

            projected.srid shouldBe SRID.WGS84
            projected.toText() shouldBe "LINESTRING (15.194231 55.257432, 15.195809 55.258328)"
        }

        should("simplify geometry to specified tolerance") {
            val wkt = "LINESTRING (0 0, 1 0, 2 4, 3 0, 4 1, 5 1, 6 0, 7 0, 8 1, 9 0, 10 1)"
            val geometry = parseWkt(wkt, SRID.UTM33)

            val simplified = geometry.simplify(1.0)

            simplified.toText() shouldBe "LINESTRING (0 0, 2 4, 3 0, 10 1)"
        }
    })
