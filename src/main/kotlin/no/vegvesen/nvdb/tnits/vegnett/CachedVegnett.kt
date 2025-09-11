package no.vegvesen.nvdb.tnits.vegnett

import no.vegvesen.nvdb.apiles.uberiket.EnumEgenskap
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt
import no.vegvesen.nvdb.tnits.extensions.today
import no.vegvesen.nvdb.tnits.measure
import no.vegvesen.nvdb.tnits.model.EgenskapsTyper
import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.model.overlaps
import no.vegvesen.nvdb.tnits.openlr.OpenLrLine
import no.vegvesen.nvdb.tnits.openlr.OpenLrNode
import no.vegvesen.nvdb.tnits.openlr.TillattRetning
import no.vegvesen.nvdb.tnits.openlr.toFormOfWay
import no.vegvesen.nvdb.tnits.storage.FeltstrekningRepository
import no.vegvesen.nvdb.tnits.storage.FunksjonellVegklasseRepository
import no.vegvesen.nvdb.tnits.storage.VeglenkerRepository
import no.vegvesen.nvdb.tnits.utstrekning
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import org.locationtech.jts.geom.Point
import org.openlr.map.FunctionalRoadClass
import java.util.concurrent.ConcurrentHashMap

class CachedVegnett(
    private val veglenkerRepository: VeglenkerRepository,
    private val feltstrekningRepository: FeltstrekningRepository,
    private val funksjonellVegklasseRepository: FunksjonellVegklasseRepository,
) {
    private lateinit var veglenkerLookup: Map<Long, List<Veglenke>>
    private val outgoingVeglenkerForward = ConcurrentHashMap<Long, MutableSet<Veglenke>>()
    private val incomingVeglenkerForward = ConcurrentHashMap<Long, MutableSet<Veglenke>>()
    private val outgoingVeglenkerReverse = ConcurrentHashMap<Long, MutableSet<Veglenke>>()
    private val incomingVeglenkerReverse = ConcurrentHashMap<Long, MutableSet<Veglenke>>()

    private val linesByVeglenkerForward = ConcurrentHashMap<Veglenke, OpenLrLine>()
    private val linesByVeglenkerReverse = ConcurrentHashMap<Veglenke, OpenLrLine>()

    private val tillattRetningByVeglenke = ConcurrentHashMap<Veglenke, Set<TillattRetning>>()

    private val frcByVeglenke = ConcurrentHashMap<Veglenke, FunctionalRoadClass>()

    private val nodes = ConcurrentHashMap<Long, OpenLrNode>()

    private var initialized = false

    fun hasRetning(veglenke: Veglenke, retning: TillattRetning): Boolean {
        require(initialized)
        return retning in (tillattRetningByVeglenke[veglenke] ?: error("Mangler tillatt retning for veglenke ${veglenke.veglenkeId}"))
    }

    fun Vegobjekt.getFrc() = when ((this.egenskaper!![EgenskapsTyper.VEGKLASSE.toString()] as? EnumEgenskap)?.verdi) {
        0 -> FunctionalRoadClass.FRC_0
        1 -> FunctionalRoadClass.FRC_1
        2 -> FunctionalRoadClass.FRC_2
        3 -> FunctionalRoadClass.FRC_3
        4 -> FunctionalRoadClass.FRC_4
        5 -> FunctionalRoadClass.FRC_5
        6 -> FunctionalRoadClass.FRC_6
        else -> FunctionalRoadClass.FRC_7
    }

    @Synchronized
    fun initialize() {
        if (initialized) return

        measure("Load veglenker") { veglenkerLookup = veglenkerRepository.getAll() }

        val funksjonellVegklasseLookup = measure("Load funksjonelle vegklasser") {
            createFunksjonellVegklasseLookup()
        }
        veglenkerLookup.forEach { (_, veglenker) ->
            veglenker.filter {
                it.isRelevant()
            }.forEach { veglenke ->

                val feltoversikt = if (veglenke.konnektering) {
                    findClosestNonKonnekteringVeglenke(veglenke, veglenker)?.feltoversikt ?: feltstrekningRepository.findFeltoversiktFromFeltstrekning(
                        veglenke,
                    )
                } else {
                    veglenke.feltoversikt
                }
                if (feltoversikt.isNotEmpty()) {
                    addVeglenke(veglenke, feltoversikt)
                    val frc = funksjonellVegklasseLookup[veglenke.veglenkesekvensId]?.entries?.firstNotNullOfOrNull { (utstrekning, frc) ->
                        if (veglenke.utstrekning.overlaps(utstrekning)) {
                            frc
                        } else {
                            null
                        }
                    } ?: FunctionalRoadClass.FRC_7
                    frcByVeglenke[veglenke] = frc
                } else {
                    // Sannsynligvis gangveg uten fartsgrense
                }
            }
        }
        initialized = true
    }

    private fun createFunksjonellVegklasseLookup(): MutableMap<Long, MutableMap<StedfestingUtstrekning, FunctionalRoadClass>> {
        val frcByVeglenkesekvens = mutableMapOf<Long, MutableMap<StedfestingUtstrekning, FunctionalRoadClass>>()

        for (vegobjekt in funksjonellVegklasseRepository.getAll()) {
            val frc = vegobjekt.getFrc()

            for (stedfesting in vegobjekt.getStedfestingLinjer().map { it.utstrekning }) {
                val veglenkesekvensId = stedfesting.veglenkesekvensId
                val map = frcByVeglenkesekvens.getOrPut(veglenkesekvensId) { mutableMapOf() }
                map[stedfesting] = frc
            }
        }

        return frcByVeglenkesekvens
    }

    private fun addVeglenke(veglenke: Veglenke, feltoversikt: List<String>) {
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

    private fun getTillattRetning(feltoversikt: List<String>): Set<TillattRetning> {
        val retninger = feltoversikt.map {
            it.takeWhile { char -> char.isDigit() }.toInt() % 2
        }.toSet()
        return when (retninger) {
            setOf(0, 1) -> setOf(TillattRetning.Med, TillattRetning.Mot)
            setOf(0) -> setOf(TillattRetning.Mot)
            setOf(1) -> setOf(TillattRetning.Med)
            else -> throw IllegalArgumentException("Ugyldig feltoversikt: $feltoversikt")
        }
    }

    private fun findClosestNonKonnekteringVeglenke(veglenke: Veglenke, veglenker: List<Veglenke>): Veglenke? =
        veglenker.filter { !it.konnektering }.minByOrNull { (it.startposisjon - veglenke.startposisjon).let { diff -> diff * diff } }

    fun getVeglenker(veglenkesekvensId: Long): List<Veglenke> {
        initialize()
        return veglenkerLookup[veglenkesekvensId]?.filter {
            it.isRelevant()
        } ?: error("Mangler veglenker for veglenkesekvensId $veglenkesekvensId")
    }

    fun Veglenke.isRelevant(): Boolean = isTopLevel &&
        typeVeg in setOf(
            TypeVeg.KANALISERT_VEG,
            TypeVeg.ENKEL_BILVEG,
            TypeVeg.GATETUN,
            TypeVeg.RAMPE,
            TypeVeg.RUNDKJORING,
            TypeVeg.TRAKTORVEG,
            TypeVeg.GAGATE,
            TypeVeg.GANG_OG_SYKKELVEG,
            TypeVeg.GANGVEG,
        ) &&
        startdato <= today &&
        (sluttdato == null || sluttdato > today)

    fun getOutgoingVeglenker(nodeId: Long, retning: TillattRetning): Set<Veglenke> {
        require(initialized)
        val outgoingVeglenker = when (retning) {
            TillattRetning.Med -> outgoingVeglenkerForward
            TillattRetning.Mot -> outgoingVeglenkerReverse
        }
        return outgoingVeglenker.computeIfAbsent(nodeId) { mutableSetOf() }
    }

    fun getIncomingVeglenker(nodeId: Long, retning: TillattRetning): Set<Veglenke> {
        require(initialized)
        val incomingVeglenker = when (retning) {
            TillattRetning.Med -> incomingVeglenkerForward
            TillattRetning.Mot -> incomingVeglenkerReverse
        }
        return incomingVeglenker.computeIfAbsent(nodeId) { mutableSetOf() }
    }

    fun getIncomingLines(id: Long, retning: TillattRetning): List<OpenLrLine> {
        require(initialized)
        return getIncomingVeglenker(id, retning).map { computeIfAbsent(it, retning) }
    }

    fun getOutgoingLines(id: Long, retning: TillattRetning): List<OpenLrLine> {
        require(initialized)
        return getOutgoingVeglenker(id, retning).map { computeIfAbsent(it, retning) }
    }

    fun getNode(nodeId: Long, retning: TillattRetning, getPoint: () -> Point): OpenLrNode {
        require(initialized)
        return nodes.computeIfAbsent(nodeId) {
            // Samme veglenke vil ikke bli lagt til dobbelt, pga set
            val incomingVeglenker = getIncomingVeglenker(nodeId, retning) + getOutgoingVeglenker(nodeId, retning.reverse())
            val outgoingVeglenker = getOutgoingVeglenker(nodeId, retning) + getIncomingVeglenker(nodeId, retning.reverse())
            OpenLrNode(
                id = nodeId,
                // Node er gyldig hvis den enten er en endenode (kun 1 tilknyttet veglenke),
                // et veikryss (mer enn 2 tilknyttede veglenker),
                // eller hvis den ligger langt nok unna enden av veglenkene (begge lengder > 100m)
                valid = incomingVeglenker.size + outgoingVeglenker.size != 2 ||
                    incomingVeglenker.size == 1 &&
                    outgoingVeglenker.size == 1 &&
                    incomingVeglenker.first().lengde > 100 &&
                    outgoingVeglenker.first().lengde > 100,
                point = getPoint(),
            )
        }
    }

    fun getLines(veglenker: List<Veglenke>, retning: TillattRetning): List<OpenLrLine> {
        require(initialized)
        return veglenker.map { veglenke ->
            computeIfAbsent(veglenke, retning)
        }
    }

    private fun computeIfAbsent(veglenke: Veglenke, retning: TillattRetning): OpenLrLine {
        synchronized(veglenke) {
            val linesByVeglenker = when (retning) {
                TillattRetning.Med -> linesByVeglenkerForward
                TillattRetning.Mot -> linesByVeglenkerReverse
            }
            return linesByVeglenker.computeIfAbsent(veglenke) {
                val frc = frcByVeglenke[veglenke] ?: FunctionalRoadClass.FRC_7
                val fow = veglenke.typeVeg.toFormOfWay()
                OpenLrLine.fromVeglenke(veglenke, frc, fow, this, retning)
            }
        }
    }
}
