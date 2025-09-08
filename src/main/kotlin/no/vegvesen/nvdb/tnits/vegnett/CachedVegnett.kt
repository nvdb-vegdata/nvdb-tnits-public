package no.vegvesen.nvdb.tnits.vegnett

import no.vegvesen.nvdb.apiles.uberiket.Detaljniva
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.openlr.OpenLrLine
import no.vegvesen.nvdb.tnits.openlr.OpenLrNode
import no.vegvesen.nvdb.tnits.openlr.TillattRetning
import no.vegvesen.nvdb.tnits.openlr.toFormOfWay
import no.vegvesen.nvdb.tnits.storage.VeglenkerRepository
import org.locationtech.jts.geom.Point
import org.openlr.map.FunctionalRoadClass
import java.util.concurrent.ConcurrentHashMap

class CachedVegnett(
    private val veglenkerRepository: VeglenkerRepository,
) {
    private lateinit var veglenkerLookup: Map<Long, List<Veglenke>>
    private val outgoingVeglenkerForward = ConcurrentHashMap<Long, MutableSet<Veglenke>>()
    private val incomingVeglenkerForward = ConcurrentHashMap<Long, MutableSet<Veglenke>>()
    private val outgoingVeglenkerReverse = ConcurrentHashMap<Long, MutableSet<Veglenke>>()
    private val incomingVeglenkerReverse = ConcurrentHashMap<Long, MutableSet<Veglenke>>()

    private val linesByVeglenkerForward = ConcurrentHashMap<Veglenke, OpenLrLine>()
    private val linesByVeglenkerReverse = ConcurrentHashMap<Veglenke, OpenLrLine>()

    private val tillattRetningByVeglenke = ConcurrentHashMap<Veglenke, Set<TillattRetning>>()

    private val nodes = ConcurrentHashMap<Long, OpenLrNode>()

    private var initialized = false

    fun hasRetning(
        veglenke: Veglenke,
        retning: TillattRetning,
    ): Boolean {
        require(initialized)
        return retning in (
            tillattRetningByVeglenke[veglenke]
                ?: error("Mangler tillatt retning for veglenke ${veglenke.veglenkeId}")
        )
    }

    @Synchronized
    fun initialize() {
        if (initialized) return

        veglenkerLookup = veglenkerRepository.getAll()
        veglenkerLookup.forEach { (_, veglenker) ->
            veglenker
                .filter {
                    it.detaljniva in setOf(Detaljniva.VEGTRASE, Detaljniva.VEGTRASE_OG_KJOREBANE)
                }.forEach { veglenke ->

                    val feltoversikt =
                        if (veglenke.konnektering) {
                            findClosestNonKonnekteringVeglenke(veglenke, veglenker)?.feltoversikt
                        } else {
                            veglenke.feltoversikt
                        }

                    check(feltoversikt != null && feltoversikt.isNotEmpty()) {
                        "Mangler feltoversikt for veglenke ${veglenke.veglenkeId}"
                    }

                    val tillattRetning = getTillattRetning(feltoversikt)

                    tillattRetningByVeglenke[veglenke] = tillattRetning

                    if (TillattRetning.Med in tillattRetning) {
                        outgoingVeglenkerForward.computeIfAbsent(veglenke.startnode) { mutableSetOf() }.add(veglenke)
                        incomingVeglenkerForward.computeIfAbsent(veglenke.sluttnode) { mutableSetOf() }.add(veglenke)
                    }
                    if (TillattRetning.Mot in tillattRetning) {
                        outgoingVeglenkerReverse.computeIfAbsent(veglenke.sluttnode) { mutableSetOf() }.add(veglenke)
                        incomingVeglenkerReverse.computeIfAbsent(veglenke.startnode) { mutableSetOf() }.add(veglenke)
                    }
                }
        }
        initialized = true
    }

    private fun getTillattRetning(feltoversikt: List<String>): Set<TillattRetning> {
        val retninger =
            feltoversikt
                .map {
                    it.takeWhile { char -> char.isDigit() }.toInt() % 2
                }.toSet()
        return when (retninger) {
            setOf(0, 1) -> setOf(TillattRetning.Med, TillattRetning.Mot)
            setOf(0) -> setOf(TillattRetning.Mot)
            setOf(1) -> setOf(TillattRetning.Med)
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
        return veglenkerLookup[veglenkesekvensId]?.filter {
            it.detaljniva in setOf(Detaljniva.VEGTRASE_OG_KJOREBANE, Detaljniva.VEGTRASE)
        } ?: error("Mangler veglenker for veglenkesekvensId $veglenkesekvensId")
    }

    fun getOutgoingVeglenker(
        nodeId: Long,
        retning: TillattRetning,
    ): Set<Veglenke> {
        require(initialized)
        val outgoingVeglenker =
            when (retning) {
                TillattRetning.Med -> outgoingVeglenkerForward
                TillattRetning.Mot -> outgoingVeglenkerReverse
            }
        return outgoingVeglenker.computeIfAbsent(nodeId) { mutableSetOf() }
    }

    fun getIncomingVeglenker(
        nodeId: Long,
        retning: TillattRetning,
    ): Set<Veglenke> {
        require(initialized)
        val incomingVeglenker =
            when (retning) {
                TillattRetning.Med -> incomingVeglenkerForward
                TillattRetning.Mot -> incomingVeglenkerReverse
            }
        return incomingVeglenker.computeIfAbsent(nodeId) { mutableSetOf() }
    }

    fun getIncomingLines(
        id: Long,
        retning: TillattRetning,
    ): List<OpenLrLine> {
        require(initialized)
        return getIncomingVeglenker(id, retning).map { computeIfAbsent(it, retning) }
    }

    fun getOutgoingLines(
        id: Long,
        retning: TillattRetning,
    ): List<OpenLrLine> {
        require(initialized)
        return getOutgoingVeglenker(id, retning).map { computeIfAbsent(it, retning) }
    }

    fun getNode(
        nodeId: Long,
        retning: TillattRetning,
        getPoint: () -> Point,
    ): OpenLrNode {
        require(initialized)
        return nodes.computeIfAbsent(nodeId) {
            OpenLrNode(
                id = nodeId,
                valid = getIncomingVeglenker(nodeId, retning).size + getOutgoingVeglenker(nodeId, retning).size != 2,
                point = getPoint(),
            )
        }
    }

    // TODO: Add lines with reversed geometry and start/end nodes for bidirectional veglenker
    // (check feltoversikt or superstedfesting.kjorefelt - odd numbers are in geometric direction)
    fun getLines(
        veglenker: List<Veglenke>,
        retning: TillattRetning,
    ): List<OpenLrLine> {
        require(initialized)
        return veglenker.map { veglenke ->
            computeIfAbsent(veglenke, retning)
        }
    }

    private fun computeIfAbsent(
        veglenke: Veglenke,
        retning: TillattRetning,
    ): OpenLrLine {
        synchronized(veglenke) {
            val linesByVeglenker =
                when (retning) {
                    TillattRetning.Med -> linesByVeglenkerForward
                    TillattRetning.Mot -> linesByVeglenkerReverse
                }
            return linesByVeglenker.computeIfAbsent(veglenke) {
                // TODO: Hent fra 821 Funksjonell Vegklasse
                val frc = FunctionalRoadClass.FRC_0
                val fow = veglenke.typeVeg.toFormOfWay()
                OpenLrLine.fromVeglenke(veglenke, frc, fow, this, retning)
            }
        }
    }
}
