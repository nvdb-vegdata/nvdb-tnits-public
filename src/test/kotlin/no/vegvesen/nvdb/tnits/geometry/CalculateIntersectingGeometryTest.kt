package no.vegvesen.nvdb.tnits.geometry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import org.locationtech.jts.geom.LineString

class CalculateIntersectingGeometryTest :
    StringSpec({

        "returns null when utstrekning objects do not overlap" {
            val wkt = "LINESTRING (500000 6600000, 500100 6600000, 500200 6600000)"
            val geometry = parseWkt(wkt, SRID.UTM33)

            val veglenkeUtstrekning = StedfestingUtstrekning(1L, 0.0, 0.5)
            val stedfestingUtstrekning = StedfestingUtstrekning(1L, 0.7, 1.0)

            val result =
                calculateIntersectingGeometry(
                    UtstrekningGeometri(veglenkeUtstrekning, geometry),
                    stedfestingUtstrekning,
                )

            result.shouldBeNull()
        }

        "returns full geometry when utstrekning objects are identical" {
            val wkt = "LINESTRING (500000 6600000, 500100 6600000, 500200 6600000)"
            val geometry = parseWkt(wkt, SRID.UTM33)

            val veglenkeUtstrekning = StedfestingUtstrekning(1L, 0.0, 1.0)
            val stedfestingUtstrekning = StedfestingUtstrekning(1L, 0.0, 1.0)
            val utstrekningGeometri = UtstrekningGeometri(veglenkeUtstrekning, geometry)

            val result = calculateIntersectingGeometry(utstrekningGeometri, stedfestingUtstrekning)

            result shouldBe utstrekningGeometri
        }

        "extracts first half of geometry when intersection covers first 50%" {
            val wkt = "LINESTRING (500000 6600000, 500100 6600000, 500200 6600000)"
            val geometry = parseWkt(wkt, SRID.UTM33)

            val veglenkeUtstrekning = StedfestingUtstrekning(1L, 0.0, 1.0)
            val stedfestingUtstrekning = StedfestingUtstrekning(1L, 0.0, 0.5)

            val result =
                calculateIntersectingGeometry(
                    UtstrekningGeometri(veglenkeUtstrekning, geometry),
                    stedfestingUtstrekning,
                )

            result.shouldNotBeNull()
            result.geometri.toText() shouldBe "LINESTRING (500000 6600000, 500100 6600000)"
            result.utstrekning shouldBe stedfestingUtstrekning
        }

        "extracts middle section of geometry when intersection is in the middle" {
            val wkt = "LINESTRING (500000 6600000, 500400 6600000)"
            val geometry = parseWkt(wkt, SRID.UTM33)

            val veglenkeUtstrekning = StedfestingUtstrekning(1L, 0.0, 1.0)
            val stedfestingUtstrekning = StedfestingUtstrekning(1L, 0.25, 0.75)

            val result =
                calculateIntersectingGeometry(
                    UtstrekningGeometri(veglenkeUtstrekning, geometry),
                    stedfestingUtstrekning,
                )

            result.shouldNotBeNull()
            result.geometri.toText() shouldBe "LINESTRING (500100 6600000, 500300 6600000)"
            result.utstrekning shouldBe stedfestingUtstrekning
        }

        "handles subset intersection within partial veglenke" {
            val wkt = "LINESTRING (500100 6600000, 500900 6600000)"
            val geometry = parseWkt(wkt, SRID.UTM33)

            val veglenkeUtstrekning = StedfestingUtstrekning(1L, 0.1, 0.9)
            val stedfestingUtstrekning = StedfestingUtstrekning(1L, 0.2, 0.8)

            val result =
                calculateIntersectingGeometry(
                    UtstrekningGeometri(veglenkeUtstrekning, geometry),
                    stedfestingUtstrekning,
                )

            result.shouldNotBeNull()
            result.geometri.toText() shouldBe "LINESTRING (500200 6600000, 500800 6600000)"
            result.utstrekning shouldBe stedfestingUtstrekning
        }

        "returns null when different veglenkesekvensId" {
            val wkt = "LINESTRING (500000 6600000, 500100 6600000)"
            val geometry = parseWkt(wkt, SRID.UTM33)
            val veglenkeUtstrekning = StedfestingUtstrekning(1L, 0.0, 1.0)
            val stedfestingUtstrekning = StedfestingUtstrekning(2L, 0.0, 0.5)

            val result =
                calculateIntersectingGeometry(
                    UtstrekningGeometri(veglenkeUtstrekning, geometry),
                    stedfestingUtstrekning,
                )

            result.shouldBeNull()
        }

        "handles single point intersections" {
            val wkt = "LINESTRING (500000 6600000, 500100 6600000, 500200 6600000)"
            val geometry = parseWkt(wkt, SRID.UTM33)
            val veglenkeUtstrekning = StedfestingUtstrekning(1L, 0.0, 1.0)
            val stedfestingUtstrekning = StedfestingUtstrekning(1L, 0.5, 0.5)

            val result =
                calculateIntersectingGeometry(
                    UtstrekningGeometri(veglenkeUtstrekning, geometry),
                    stedfestingUtstrekning,
                )

            result.shouldNotBeNull()
            result.geometri.shouldBeInstanceOf<LineString>()
            result.geometri.length shouldBe 0.0
            result.geometri.toText() shouldBe "LINESTRING (500100 6600000, 500100 6600000)"
            result.utstrekning shouldBe stedfestingUtstrekning
        }

        "handles partial overlap" {
            val wkt = "LINESTRING (500500 6600000, 500900 6600000)"
            val geometry = parseWkt(wkt, SRID.UTM33)
            val veglenkeUtstrekning = StedfestingUtstrekning(1L, 0.5, 0.9)
            val stedfestingUtstrekning = StedfestingUtstrekning(1L, 0.6, 1.0)

            val result =
                calculateIntersectingGeometry(
                    UtstrekningGeometri(veglenkeUtstrekning, geometry),
                    stedfestingUtstrekning,
                )

            result.shouldNotBeNull()
            result.geometri.toText() shouldBe "LINESTRING (500600 6600000, 500900 6600000)"
            result.utstrekning shouldBe StedfestingUtstrekning(1L, 0.6, 0.9)
        }

        "handles stedfesting that extends beyond veglenke" {
            val wkt = "LINESTRING (500000 6600000, 500100 6600000, 500200 6600000)"
            val geometry = parseWkt(wkt, SRID.UTM33)
            val veglenkeUtstrekning = StedfestingUtstrekning(1L, 0.3, 0.7)
            val stedfestingUtstrekning = StedfestingUtstrekning(1L, 0.0, 1.0)
            val utstrekningGeometri = UtstrekningGeometri(veglenkeUtstrekning, geometry)

            val result = calculateIntersectingGeometry(utstrekningGeometri, stedfestingUtstrekning)

            result shouldBe utstrekningGeometri
        }
    })
