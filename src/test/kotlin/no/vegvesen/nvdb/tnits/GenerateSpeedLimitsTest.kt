package no.vegvesen.nvdb.tnits

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.Sideposisjon
import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.vegobjekter.VegobjektStedfesting

class GenerateSpeedLimitsTest :
    StringSpec({

        "include all fields when mapping from VegobjektStedfesting to utstrekning" {
            VegobjektStedfesting(
                vegobjektId = 1,
                vegobjektType = 105,
                veglenkesekvensId = 123,
                startposisjon = 0.0,
                sluttposisjon = 1.0,
                retning = Retning.MOT,
                sideposisjon = Sideposisjon.V,
                kjorefelt = listOf("2"),
            ).utstrekning shouldBe
                StedfestingUtstrekning(
                    veglenkesekvensId = 123,
                    startposisjon = 0.0,
                    sluttposisjon = 1.0,
                    retning = Retning.MOT,
                    kjorefelt = listOf("2"),
                )
        }
    })
