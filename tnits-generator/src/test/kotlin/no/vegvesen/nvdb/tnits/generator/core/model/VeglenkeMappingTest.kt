package no.vegvesen.nvdb.tnits.generator.core.model

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.toKotlinLocalDate
import no.vegvesen.nvdb.apiles.uberiket.*
import java.time.LocalDate
import no.vegvesen.nvdb.apiles.uberiket.Veglenke as ApiVeglenke

class VeglenkeMappingTest : ShouldSpec({

    should("include currently active veglenker") {
        val today = LocalDate.parse("2022-06-15").toKotlinLocalDate()

        fun ApiVeglenke.setCommonProps() {
            startport = 1
            sluttport = 2
            geometri = Geometristruktur().apply {
                wkt = "LINESTRING Z (500000 7000000 0, 500100 7000100 0)"
                srid = SRID._5973
                lengde = 141.42
            }
            typeVeg = TypeVeg.KANALISERT_VEG
            detaljniva = Detaljniva.VEGTRASE_OG_KJOREBANE
            konnektering = false
            kommune = 5001
        }

        val apiVeglenkesekvens = Veglenkesekvens().apply {
            id = 1
            porter = listOf(
                Veglenkesekvensport().apply {
                    nummer = 1
                    nodeId = 2
                    posisjon = 0.0
                },
                Veglenkesekvensport().apply {
                    nummer = 2
                    nodeId = 3
                    posisjon = 1.0
                },
            )
            veglenker = listOf(
                ApiVeglenke().apply {
                    // Valid: no end date
                    setCommonProps()
                    nummer = 1
                    gyldighetsperiode = Gyldighetsperiode().apply {
                        startdato = LocalDate.parse("2020-01-01")
                    }
                },
                ApiVeglenke().apply {
                    // Not valid: end date today
                    setCommonProps()
                    nummer = 2
                    gyldighetsperiode = Gyldighetsperiode().apply {
                        startdato = LocalDate.parse("2020-01-01")
                        sluttdato = LocalDate.parse("2022-06-15")
                    }
                },
                ApiVeglenke().apply {
                    // Valid: end date tomorrow
                    setCommonProps()
                    nummer = 3
                    gyldighetsperiode = Gyldighetsperiode().apply {
                        startdato = LocalDate.parse("2020-01-01")
                        sluttdato = LocalDate.parse("2022-06-16")
                    }
                },
                ApiVeglenke().apply {
                    // Valid: start date today
                    setCommonProps()
                    nummer = 4
                    gyldighetsperiode = Gyldighetsperiode().apply {
                        startdato = LocalDate.parse("2022-06-15")
                    }
                },
                ApiVeglenke().apply {
                    // Valid: start date in the future (will be filtered out on export, but must be imported now)
                    setCommonProps()
                    nummer = 5
                    gyldighetsperiode = Gyldighetsperiode().apply {
                        startdato = LocalDate.parse("2022-06-16")
                    }
                },
            )
        }

        val domainVeglenker = apiVeglenkesekvens.convertToDomainVeglenker(today)

        domainVeglenker.map { it.veglenkenummer } shouldBe listOf(1, 3, 4, 5)
    }
})
