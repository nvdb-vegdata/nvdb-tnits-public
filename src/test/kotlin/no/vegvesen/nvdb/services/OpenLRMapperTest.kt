package no.vegvesen.nvdb.services

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class OpenLRMapperTest :
    StringSpec({

        "OpenLRMapper should generate valid OpenLR XML" {
            val mapper = OpenLRMapper()

            val result = mapper.mapToOpenLR(12345L, 67890)

            result shouldNotBe null
            result shouldContain "OpenLR"
            result shouldContain "XMLLocationReference"
            result shouldContain "LineLocationReference"
            result shouldContain "LocationReferencePoint"
            result shouldContain "Coordinates"
            result shouldContain "59.9139"
            result shouldContain "10.7522"
            result shouldContain "LineAttributes"
            result shouldContain "FRC3"
            result shouldContain "MULTIPLE_CARRIAGEWAY"
            result shouldContain "PathAttributes"
            result shouldContain "LFRCNP"
            result shouldContain "DNP"
            result shouldContain "LastLocationReferencePoint"
        }
    })
