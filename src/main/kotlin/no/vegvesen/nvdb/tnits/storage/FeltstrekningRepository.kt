package no.vegvesen.nvdb.tnits.storage

import no.vegvesen.nvdb.apiles.uberiket.TekstEgenskap
import no.vegvesen.nvdb.tnits.database.Stedfestinger
import no.vegvesen.nvdb.tnits.database.Vegobjekter
import no.vegvesen.nvdb.tnits.model.EgenskapsTyper
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class FeltstrekningRepository {
    fun findFeltoversiktFromFeltstrekning(veglenke: Veglenke): List<String> = transaction {
        val feltstrekning =
            Vegobjekter
                .innerJoin(Stedfestinger)
                .select(Vegobjekter.data)
                .where {
                    (Vegobjekter.vegobjektType eq VegobjektTyper.FELTSTREKNING) and
                        (Stedfestinger.veglenkesekvensId eq veglenke.veglenkesekvensId) and
                        (Stedfestinger.sluttposisjon greater veglenke.startposisjon) and
                        (Stedfestinger.startposisjon less veglenke.sluttposisjon)
                }.limit(1)
                .map { it[Vegobjekter.data] }
                .firstOrNull() ?: return@transaction emptyList<String>()

        (feltstrekning.egenskaper!![EgenskapsTyper.FELTOVERSIKT_I_VEGLENKERETNING.toString()] as TekstEgenskap).verdi.split(
            "#",
        )
    }
}
