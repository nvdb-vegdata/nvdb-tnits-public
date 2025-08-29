package no.vegvesen.nvdb.tnits.vegnett

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
    private lateinit var veglenker: Map<Long, List<Veglenke>>
    private val outgoingVeglenker = ConcurrentHashMap<Long, MutableSet<Veglenke>>()
    private val incomingVeglenker = ConcurrentHashMap<Long, MutableSet<Veglenke>>()

    private val linesByVeglenker = ConcurrentHashMap<Veglenke, OpenLrLine>()

    private val nodes = ConcurrentHashMap<Long, OpenLrNode>()

    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return

        veglenker = veglenkerRepository.getAll()
        veglenker.values.forEach { veglenker ->
            veglenker.forEach {
                outgoingVeglenker.computeIfAbsent(it.startnode) { mutableSetOf() }.add(it)
                incomingVeglenker.computeIfAbsent(it.sluttnode) { mutableSetOf() }.add(it)
            }
        }
        initialized = true
    }

    fun getVeglenker(veglenkesekvensId: Long): List<Veglenke> {
        initialize()
        return veglenker[veglenkesekvensId] ?: emptyList()
    }

    fun getOutgoingVeglenker(nodeId: Long): Set<Veglenke> {
        initialize()
        return outgoingVeglenker[nodeId] ?: emptySet()
    }

    fun getIncomingVeglenker(nodeId: Long): Set<Veglenke> {
        initialize()
        return incomingVeglenker[nodeId] ?: emptySet()
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
