package no.vegvesen.nvdb.tnits.generator.core.api

import no.vegvesen.nvdb.tnits.generator.core.model.IdRange
import no.vegvesen.nvdb.tnits.generator.core.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.generator.core.model.Vegobjekt
import no.vegvesen.nvdb.tnits.generator.core.services.storage.WriteBatchContext

typealias DirtyVeglenkesekvenser = Set<Long>

interface VegobjekterRepository {
    context(_: WriteBatchContext)
    fun batchUpdate(vegobjektType: Int, updates: Map<Long, Vegobjekt?>): DirtyVeglenkesekvenser

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
    fun cleanOldVersions(vegobjektType: Int)
    fun getVegobjektStedfestingLookup(vegobjektType: Int, veglenkesekvensIds: List<Long>): Map<Long, List<Vegobjekt>>

    fun clearVegobjektType(vegobjektTypeId: Int)
}
