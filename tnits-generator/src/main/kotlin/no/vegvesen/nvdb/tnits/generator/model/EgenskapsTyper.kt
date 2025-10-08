package no.vegvesen.nvdb.tnits.generator.model

import org.openlr.map.FunctionalRoadClass

object EgenskapsTyper {
    const val FARTSGRENSE = 2021
    const val VEGKLASSE = 9338
    const val FELTOVERSIKT_I_VEGLENKERETNING = 5528
}

fun EnumVerdi.toFrc() = when (verdi) {
    13060 -> FunctionalRoadClass.FRC_0
    13061 -> FunctionalRoadClass.FRC_1
    13062 -> FunctionalRoadClass.FRC_2
    13063 -> FunctionalRoadClass.FRC_3
    13064 -> FunctionalRoadClass.FRC_4
    13065 -> FunctionalRoadClass.FRC_5
    13066 -> FunctionalRoadClass.FRC_6
    else -> FunctionalRoadClass.FRC_7
}
