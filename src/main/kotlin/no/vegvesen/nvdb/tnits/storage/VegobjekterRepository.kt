package no.vegvesen.nvdb.tnits.storage

import no.vegvesen.nvdb.tnits.IdRange
import no.vegvesen.nvdb.tnits.model.*
import org.openlr.map.FunctionalRoadClass

interface VegobjekterRepository {
    context(_: WriteBatchContext)
    fun batchUpdate(vegobjektType: Int, updates: Map<Long, Vegobjekt?>)

    context(_: WriteBatchContext)
    fun batchInsert(vegobjektType: Int, vegobjekter: List<Vegobjekt>)

    fun findOverlappingVegobjekter(utstrekning: StedfestingUtstrekning, vegobjektType: Int): List<Vegobjekt>

    fun findFeltoversiktFromFeltstrekning(veglenke: Veglenke): List<String> {
        val overlappingFeltstrekning = findOverlappingVegobjekter(veglenke, VegobjektTyper.FELTSTREKNING)
            .firstOrNull()
        return overlappingFeltstrekning?.let { it.egenskaper[EgenskapsTyper.FELTOVERSIKT_I_VEGLENKERETNING] as? TekstVerdi }?.verdi?.split("#") ?: emptyList()
    }

    // Høyeste numeriske verdi er laveste viktighet (utnytter at enum-IDer er i stigende rekkefølge
    fun findFrcForVeglenke(veglenke: Veglenke): FunctionalRoadClass = findOverlappingVegobjekter(veglenke, VegobjektTyper.FUNKSJONELL_VEGKLASSE)
        .mapNotNull { it.egenskaper[EgenskapsTyper.VEGKLASSE] as? EnumVerdi }
        .maxByOrNull { it.verdi }
        ?.toFrc()
        ?: FunctionalRoadClass.FRC_7

    fun findVegobjektIds(vegobjektType: Int): Sequence<Long>
    fun findVegobjekter(vegobjektType: Int, idRange: IdRange): List<Vegobjekt>
    fun getAll(vegobjektType: Int): List<Vegobjekt>

    fun streamAll(vegobjektType: Int): Sequence<Vegobjekt>

    fun getVegobjektStedfestingLookup(vegobjektType: Int): Map<Long, List<Vegobjekt>>
}
