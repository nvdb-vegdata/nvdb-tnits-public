package no.vegvesen.nvdb.tnits.generator.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID
import no.vegvesen.nvdb.tnits.generator.core.extensions.parseWkt
import no.vegvesen.nvdb.apiles.uberiket.Veglenkesekvens as ApiVeglenkesekvens

fun ApiVeglenkesekvens.toDomain(today: LocalDate): Veglenkesekvens = Veglenkesekvens(
    id,
    convertToDomainVeglenker(today),
)

/**
 * Konverterer en [ApiVeglenkesekvens] fra Uberiket API til en liste av domenemodellen [Veglenke].
 * - Filtrer til bare aktive veglenker.
 * - Mapper start- og sluttporter til posisjoner og noder.
 * - Sorterer veglenkene etter startposisjon.
 */
fun ApiVeglenkesekvens.convertToDomainVeglenker(today: LocalDate): List<Veglenke> {
    val portLookup = this.porter.associateBy { it.nummer }

    return this.veglenker
        .map { veglenke ->

            val startport =
                portLookup[veglenke.startport]
                    ?: error("Startport ${veglenke.startport} not found in veglenkesekvens ${this.id}")
            val sluttport =
                portLookup[veglenke.sluttport]
                    ?: error("Sluttport ${veglenke.sluttport} not found in veglenkesekvens ${this.id}")

            val srid = veglenke.geometri.srid.value.toInt()

            check(srid == SRID.EPSG5973)

            Veglenke(
                veglenkesekvensId = this.id,
                veglenkenummer = veglenke.nummer,
                startposisjon = startport.posisjon,
                sluttposisjon = sluttport.posisjon,
                startnode = startport.nodeId,
                sluttnode = sluttport.nodeId,
                startdato = veglenke.gyldighetsperiode.startdato.toKotlinLocalDate(),
                sluttdato = veglenke.gyldighetsperiode.sluttdato?.toKotlinLocalDate(),
                // 3D -> 2D
                geometri = parseWkt(veglenke.geometri.wkt, SRID.UTM33),
                typeVeg = veglenke.typeVeg,
                detaljniva = veglenke.detaljniva,
                feltoversikt = veglenke.feltoversikt,
                lengde = veglenke.geometri.lengde ?: 0.0,
                konnektering = veglenke.konnektering,
                superstedfesting = veglenke.superstedfesting?.let { stedfesting ->
                    Superstedfesting(
                        veglenksekvensId = stedfesting.id,
                        startposisjon = stedfesting.startposisjon,
                        sluttposisjon = stedfesting.sluttposisjon,
                        kjorefelt = stedfesting.kjorefelt,
                    )
                },
                kommune = veglenke.kommune,
            )
        }
        .filter { veglenke -> veglenke.sluttdato == null || veglenke.sluttdato > today }
        .sortedBy { it.startposisjon }
}
