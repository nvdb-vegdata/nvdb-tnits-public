package no.vegvesen.nvdb.tnits.vegnett

import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.openlr.OpenLrLine
import no.vegvesen.nvdb.tnits.openlr.OpenLrNode
import no.vegvesen.nvdb.tnits.openlr.toFormOfWay
import no.vegvesen.nvdb.tnits.storage.VeglenkerRepository
import org.locationtech.jts.geom.Point
import org.openlr.map.FunctionalRoadClass
import java.util.concurrent.ConcurrentHashMap

class CachedVegnett(
    private val veglenkerRepository: VeglenkerRepository,
) {
    private lateinit var veglenkerLookup: Map<Long, List<Veglenke>>
    private val outgoingVeglenker = ConcurrentHashMap<Long, MutableSet<Veglenke>>()
    private val incomingVeglenker = ConcurrentHashMap<Long, MutableSet<Veglenke>>()

    private val linesByVeglenker = ConcurrentHashMap<Veglenke, OpenLrLine>()

    private val nodes = ConcurrentHashMap<Long, OpenLrNode>()

    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return

        veglenkerLookup = veglenkerRepository.getAll()
        veglenkerLookup.forEach { (_, veglenker) ->
            veglenker.forEach { veglenke ->

//                val feltoversikt =
//                    if (veglenke.konnektering) {
//                        findClosestNonKonnekteringVeglenke(veglenke, veglenker)?.feltoversikt
//                    } else {
//                        veglenke.feltoversikt
//                    }
//
//                check(feltoversikt != null && feltoversikt.isNotEmpty()) {
//                    "Mangler feltoversikt for veglenke ${veglenke.veglenkeId}"
//                }
//
//                val tillattRetning = getTillattRetning(feltoversikt)

                outgoingVeglenker.computeIfAbsent(veglenke.startnode) { mutableSetOf() }.add(veglenke)
                outgoingVeglenker.computeIfAbsent(veglenke.sluttnode) { mutableSetOf() }
                incomingVeglenker.computeIfAbsent(veglenke.sluttnode) { mutableSetOf() }.add(veglenke)
                incomingVeglenker.computeIfAbsent(veglenke.startnode) { mutableSetOf() }
            }
        }
        initialized = true
    }

    private fun getTillattRetning(feltoversikt: List<String>): List<Retning> {
        val retninger =
            feltoversikt
                .map {
                    it.takeWhile { char -> char.isDigit() }.toInt() % 2
                }.toSet()
        return when (retninger) {
            setOf(0, 1) -> listOf(Retning.MED, Retning.MOT)
            setOf(0) -> listOf(Retning.MOT)
            setOf(1) -> listOf(Retning.MED)
            else -> throw IllegalArgumentException("Ugyldig feltoversikt: $feltoversikt")
        }
    }

    private fun findClosestNonKonnekteringVeglenke(
        veglenke: Veglenke,
        veglenker: List<Veglenke>,
    ): Veglenke? =
        veglenker
            .filter { !it.konnektering }
            .minByOrNull { (it.startposisjon - veglenke.startposisjon).let { diff -> diff * diff } }

    fun getVeglenker(veglenkesekvensId: Long): List<Veglenke> {
        initialize()
        return veglenkerLookup[veglenkesekvensId] ?: error("Mangler veglenker for veglenkesekvensId $veglenkesekvensId")
    }

    fun getOutgoingVeglenker(nodeId: Long): Set<Veglenke> {
        initialize()
        return outgoingVeglenker[nodeId] ?: error("Mangler outgoing veglenker for nodeId $nodeId")
    }

    fun getIncomingVeglenker(nodeId: Long): Set<Veglenke> {
        initialize()
        return incomingVeglenker[nodeId] ?: error("Mangler incoming veglenker for nodeId $nodeId")
    }

    fun getIncomingLines(id: Long): List<OpenLrLine> = getIncomingVeglenker(id).map { getOrPut(it) }

    fun getOutgoingLines(id: Long): List<OpenLrLine> = getOutgoingVeglenker(id).map { getOrPut(it) }

    fun getNode(
        nodeId: Long,
        getPoint: () -> Point,
    ): OpenLrNode =
        nodes.getOrPut(nodeId) {
            OpenLrNode(
                id = nodeId,
                valid = getIncomingVeglenker(nodeId).size + getOutgoingVeglenker(nodeId).size <= 2,
                point = getPoint(),
            )
        }

    // TODO: Add lines with reversed geometry and start/end nodes for bidirectional veglenker
    // (check feltoversikt or superstedfesting.kjorefelt - odd numbers are in geometric direction)
    fun getLines(veglenker: List<Veglenke>): List<OpenLrLine> =
        veglenker.map { veglenke ->
            getOrPut(veglenke)
        }

    private fun getOrPut(veglenke: Veglenke): OpenLrLine =
        linesByVeglenker.getOrPut(veglenke) {
            // TODO: Hent fra 821 Funksjonell Vegklasse
            val frc = FunctionalRoadClass.FRC_0
            val fow = veglenke.typeVeg.toFormOfWay()
            OpenLrLine.fromVeglenke(veglenke, frc, fow, this)
        }
}
