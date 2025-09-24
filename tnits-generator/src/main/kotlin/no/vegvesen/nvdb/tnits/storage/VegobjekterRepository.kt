package no.vegvesen.nvdb.tnits.storage

import no.vegvesen.nvdb.tnits.IdRange
import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.model.Vegobjekt
import no.vegvesen.nvdb.tnits.model.VegobjektUpdate

interface VegobjekterRepository {
    context(_: WriteBatchContext)
    fun batchUpdate(vegobjektType: Int, updates: Map<Long, VegobjektUpdate>)

    context(_: WriteBatchContext)
    fun batchInsert(vegobjektType: Int, vegobjekter: List<Vegobjekt>)

    fun findOverlappingVegobjekter(utstrekning: StedfestingUtstrekning, vegobjektType: Int): List<Vegobjekt>

    fun findVegobjektIds(vegobjektType: Int): Sequence<Long>
    fun findVegobjekter(vegobjektType: Int, idRange: IdRange): List<Vegobjekt>
    fun getAll(vegobjektType: Int): List<Vegobjekt>

    fun streamAll(vegobjektType: Int): Sequence<Vegobjekt>

    fun getVegobjektStedfestingLookup(vegobjektType: Int): Map<Long, List<Vegobjekt>>
    fun countVegobjekter(vegobjektType: Int): Int
    fun findVegobjekter(vegobjektType: Int, ids: Collection<Long>): Map<Long, Vegobjekt?>
}
