package no.vegvesen.nvdb.tnits.storage

import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.tnits.IdRange
import no.vegvesen.nvdb.tnits.model.*
import no.vegvesen.nvdb.tnits.utstrekning
import org.openlr.map.FunctionalRoadClass
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

interface VegobjekterRepository {
    context(_: WriteBatchContext)
    fun batchUpdate(vegobjektType: Int, updates: Map<Long, ApiVegobjekt?>, validFromById: Map<Long, LocalDate>)

    context(_: WriteBatchContext)
    fun batchInsert(vegobjektType: Int, vegobjekter: List<ApiVegobjekt>, validFromById: Map<Long, LocalDate>)
    fun findOverlappingVegobjekter(utstrekning: StedfestingUtstrekning, vegobjektType: Int): List<Vegobjekt>

    fun findFeltoversiktFromFeltstrekning(veglenke: Veglenke): List<String> {
        val overlappingFeltstrekning = findOverlappingVegobjekter(veglenke.utstrekning, VegobjektTyper.FELTSTREKNING)
            .firstOrNull()
        return overlappingFeltstrekning?.let { it.egenskaper[EgenskapsTyper.FELTOVERSIKT_I_VEGLENKERETNING] as? TekstVerdi }?.verdi?.split("#") ?: emptyList()
    }

    // Høyeste numeriske verdi er laveste viktighet (utnytter at enum-IDer er i stigende rekkefølge
    fun findFrcForVeglenke(veglenke: Veglenke): FunctionalRoadClass = findOverlappingVegobjekter(veglenke.utstrekning, VegobjektTyper.FUNKSJONELL_VEGKLASSE)
        .mapNotNull { it.egenskaper[EgenskapsTyper.VEGKLASSE] as? EnumVerdi }
        .maxByOrNull { it.verdi }
        ?.toFrc()
        ?: FunctionalRoadClass.FRC_7

    fun findVegobjektIds(vegobjektType: Int): Sequence<Long>
    fun findVegobjekter(vegobjektType: Int, idRange: IdRange): List<Vegobjekt>
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
