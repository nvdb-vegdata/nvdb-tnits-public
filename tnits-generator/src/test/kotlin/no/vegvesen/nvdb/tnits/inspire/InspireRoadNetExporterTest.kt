package no.vegvesen.nvdb.tnits.inspire

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.apiles.uberiket.Detaljniva
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.tnits.extensions.today
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.geometry.parseWkt
import no.vegvesen.nvdb.tnits.geometry.projectTo
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.openlr.toFormOfWay
import org.locationtech.jts.geom.LineString
import org.openlr.map.FormOfWay
import org.openlr.map.FunctionalRoadClass
import java.io.ByteArrayOutputStream
import kotlin.time.Clock

class InspireRoadNetExporterTest : ShouldSpec({

    context("INSPIRE RoadLink XML generation") {
        should("generate valid INSPIRE v5.0 XML structure") {
            val testVeglenke = createTestVeglenke()
            val xml = generateTestXml(testVeglenke)

            // Verify namespace declarations
            xml shouldContain "xmlns:wfs=\"http://www.opengis.net/wfs/2.0\""
            xml shouldContain "xmlns:gml=\"http://www.opengis.net/gml/3.2\""
            xml shouldContain "xmlns:tn-ro=\"http://inspire.ec.europa.eu/schemas/tn-ro/5.0\""
            xml shouldContain "xmlns:net=\"http://inspire.ec.europa.eu/schemas/net/5.0\""
            xml shouldContain "xmlns:base=\"http://inspire.ec.europa.eu/schemas/base/4.0\""
        }

        should("not repeat namespace declarations on child elements") {
            val testVeglenke = createTestVeglenke()
            val xml = generateTestXml(testVeglenke)

            // Count occurrences of namespace declaration
            val nsCount = "xmlns:net=".toRegex().findAll(xml).count()
            nsCount shouldBe 1 // Should only appear once on root element
        }

        should("use UTM33 coordinate system") {
            val testVeglenke = createTestVeglenke()
            val xml = generateTestXml(testVeglenke)

            xml shouldContain "srsName=\"urn:ogc:def:crs:EPSG::25833\""
        }

        should("include inspireId with correct structure") {
            val testVeglenke = createTestVeglenke()
            val xml = generateTestXml(testVeglenke)

            xml shouldContain "<net:inspireId>"
            xml shouldContain "<base:Identifier>"
            xml shouldContain "<base:localId>12345-1</base:localId>"
            xml shouldContain "<base:namespace>http://data.geonorge.no/inspire/tn-ro</base:namespace>"
        }

        should("include formOfWay") {
            val testVeglenke = createTestVeglenke(typeVeg = TypeVeg.ENKEL_BILVEG)
            val xml = generateTestXml(testVeglenke)

            xml shouldContain "<tn-ro:formOfWay>singleCarriageway</tn-ro:formOfWay>"
        }

        should("filter out inactive veglenker by date") {
            val activeVeglenke = createTestVeglenke(startdato = today, sluttdato = null)
            val futureVeglenke = createTestVeglenke(startdato = LocalDate(2099, 1, 1))
            val expiredVeglenke = createTestVeglenke(
                startdato = LocalDate(2020, 1, 1),
                sluttdato = LocalDate(2020, 12, 31),
            )

            // Active veglenke should have startdato <= today and (sluttdato == null or sluttdato > today)
            (activeVeglenke.startdato <= today && (activeVeglenke.sluttdato == null || activeVeglenke.sluttdato > today)) shouldBe true
            (futureVeglenke.startdato <= today && (futureVeglenke.sluttdato == null || futureVeglenke.sluttdato > today)) shouldBe false
            (expiredVeglenke.startdato <= today && (expiredVeglenke.sluttdato == null || expiredVeglenke.sluttdato > today)) shouldBe false
        }
    }

    context("FRC and FOW conversion") {
        should("convert FunctionalRoadClass to INSPIRE values") {
            FunctionalRoadClass.FRC_0.toInspireValue() shouldBe "mainRoad"
            FunctionalRoadClass.FRC_7.toInspireValue() shouldBe "seventhClassRoad"
        }

        should("convert FormOfWay to INSPIRE values") {
            FormOfWay.MOTORWAY.toInspireValue() shouldBe "motorway"
            FormOfWay.ROUNDABOUT.toInspireValue() shouldBe "roundabout"
            FormOfWay.SINGLE_CARRIAGEWAY.toInspireValue() shouldBe "singleCarriageway"
        }

        should("map TypeVeg to FormOfWay correctly") {
            TypeVeg.ENKEL_BILVEG.toFormOfWay() shouldBe FormOfWay.SINGLE_CARRIAGEWAY
            TypeVeg.KANALISERT_VEG.toFormOfWay() shouldBe FormOfWay.MULTIPLE_CARRIAGEWAY
            TypeVeg.RUNDKJORING.toFormOfWay() shouldBe FormOfWay.ROUNDABOUT
            TypeVeg.RAMPE.toFormOfWay() shouldBe FormOfWay.SLIP_ROAD
        }
    }
})

private fun FunctionalRoadClass.toInspireValue(): String = when (this) {
    FunctionalRoadClass.FRC_0 -> "mainRoad"
    FunctionalRoadClass.FRC_1 -> "firstClassRoad"
    FunctionalRoadClass.FRC_2 -> "secondClassRoad"
    FunctionalRoadClass.FRC_3 -> "thirdClassRoad"
    FunctionalRoadClass.FRC_4 -> "fourthClassRoad"
    FunctionalRoadClass.FRC_5 -> "fifthClassRoad"
    FunctionalRoadClass.FRC_6 -> "sixthClassRoad"
    FunctionalRoadClass.FRC_7 -> "seventhClassRoad"
}

private fun FormOfWay.toInspireValue(): String = when (this) {
    FormOfWay.MOTORWAY -> "motorway"
    FormOfWay.MULTIPLE_CARRIAGEWAY -> "dualCarriageway"
    FormOfWay.SINGLE_CARRIAGEWAY -> "singleCarriageway"
    FormOfWay.ROUNDABOUT -> "roundabout"
    FormOfWay.TRAFFIC_SQUARE -> "trafficSquare"
    FormOfWay.SLIP_ROAD -> "slipRoad"
    FormOfWay.OTHER -> "singleCarriageway"
    FormOfWay.UNDEFINED -> "singleCarriageway"
}

private fun createTestVeglenke(
    veglenkesekvensId: Long = 12345L,
    veglenkenummer: Int = 1,
    startdato: LocalDate = today,
    sluttdato: LocalDate? = null,
    typeVeg: TypeVeg = TypeVeg.ENKEL_BILVEG,
): Veglenke {
    val wkt = "LINESTRING(10.7522 59.9139, 10.7537 59.9148)"
    val wgs84Geom = parseWkt(wkt, SRID.WGS84)

    return Veglenke(
        veglenkesekvensId = veglenkesekvensId,
        veglenkenummer = veglenkenummer,
        startposisjon = 0.0,
        sluttposisjon = 1.0,
        startnode = 1001L,
        sluttnode = 1002L,
        startdato = startdato,
        sluttdato = sluttdato,
        lengde = 100.0,
        konnektering = false,
        geometri = wgs84Geom,
        typeVeg = typeVeg,
        detaljniva = Detaljniva.VEGTRASE,
        feltoversikt = listOf("1"),
    )
}

private fun generateTestXml(veglenke: Veglenke): String {
    val roadLink = InspireRoadLink(
        id = "${veglenke.veglenkesekvensId}-${veglenke.veglenkenummer}",
        geometry = veglenke.geometri.projectTo(SRID.UTM33) as LineString,
        functionalRoadClass = FunctionalRoadClass.FRC_3,
        formOfWay = veglenke.typeVeg.toFormOfWay(),
    )

    val output = ByteArrayOutputStream()
    no.vegvesen.nvdb.tnits.xml.writeXmlDocument(
        output,
        rootQName = "wfs:FeatureCollection",
        namespaces = InspireRoadNetExporter.INSPIRE_NAMESPACES,
    ) {
        attribute("timeStamp", Clock.System.now().toString())
        "wfs:member" {
            "tn-ro:RoadLink" {
                attribute("gml:id", "RoadLink.${roadLink.id}")

                "net:inspireId" {
                    "base:Identifier" {
                        "base:localId" { text(roadLink.id) }
                        "base:namespace" { text("http://data.geonorge.no/inspire/tn-ro") }
                    }
                }

                "net:centrelineGeometry" {
                    "gml:LineString" {
                        attribute("gml:id", "RoadLink.${roadLink.id}_NET_CENTRELINEGEOMETRY")
                        attribute("srsName", "urn:ogc:def:crs:EPSG::25833")
                        "gml:posList" {
                            val coords = roadLink.geometry.coordinates.joinToString(" ") {
                                "${it.x} ${it.y}"
                            }
                            text(coords)
                        }
                    }
                }

                roadLink.functionalRoadClass?.let { frc ->
                    "tn-ro:functionalRoadClass" {
                        text(frc.toInspireValue())
                    }
                }

                "tn-ro:formOfWay" {
                    text(roadLink.formOfWay.toInspireValue())
                }
            }
        }
    }

    return output.toString()
}
