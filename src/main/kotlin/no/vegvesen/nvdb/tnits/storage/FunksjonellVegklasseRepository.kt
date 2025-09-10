package no.vegvesen.nvdb.tnits.storage

import no.vegvesen.nvdb.apiles.uberiket.EnumEgenskap
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt
import no.vegvesen.nvdb.tnits.database.Stedfestinger
import no.vegvesen.nvdb.tnits.database.Vegobjekter
import no.vegvesen.nvdb.tnits.model.EgenskapsTyper
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.openlr.map.FunctionalRoadClass

class FunksjonellVegklasseRepository {
    fun findFunksjonellVegklasse(veglenke: Veglenke): FunctionalRoadClass =
        transaction {
            // Laveste viktighet er den høyeste numeriske verdien
            val lowestFrc =
                Vegobjekter
                    .innerJoin(Stedfestinger)
                    .select(Vegobjekter.data)
                    .where {
                        (Vegobjekter.vegobjektType eq VegobjektTyper.FUNKSJONELL_VEGKLASSE) and
                            (Stedfestinger.veglenkesekvensId eq veglenke.veglenkesekvensId) and
                            (Stedfestinger.sluttposisjon greater veglenke.startposisjon) and
                            (Stedfestinger.startposisjon less veglenke.sluttposisjon)
                    }.maxOfOrNull { (it[Vegobjekter.data].egenskaper!![EgenskapsTyper.VEGKLASSE.toString()] as EnumEgenskap).verdi }

            when (lowestFrc) {
                0 -> FunctionalRoadClass.FRC_0
                1 -> FunctionalRoadClass.FRC_1
                2 -> FunctionalRoadClass.FRC_2
                3 -> FunctionalRoadClass.FRC_3
                4 -> FunctionalRoadClass.FRC_4
                5 -> FunctionalRoadClass.FRC_5
                6 -> FunctionalRoadClass.FRC_6
                else -> FunctionalRoadClass.FRC_7
            }
        }

    fun getAll(): List<Vegobjekt> =
        transaction {
            Vegobjekter
                .select(Vegobjekter.data)
                .map { it[Vegobjekter.data] }
        }
}
