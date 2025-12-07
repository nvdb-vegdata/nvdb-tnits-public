package no.vegvesen.nvdb.tnits.generator.core.model

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldBeEmpty
import no.vegvesen.nvdb.tnits.generator.objectMapper
import no.vegvesen.nvdb.tnits.generator.readApiVegobjekt

class VegobjektMappingTest : ShouldSpec({

    should("map høydebegrensning without skilta høyde egenskap") {
        val apiVegobjekt = objectMapper.readApiVegobjekt("vegobjekt-591-848324148.json")

        val vegobjekt = apiVegobjekt.toDomain()

        vegobjekt.egenskaper.shouldBeEmpty()
    }
})
