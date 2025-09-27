package no.vegvesen.nvdb.tnits.vegnett

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.tnits.extensions.today
import no.vegvesen.nvdb.tnits.model.*
import no.vegvesen.nvdb.tnits.openlr.OpenLrLine
import no.vegvesen.nvdb.tnits.openlr.OpenLrNode
import no.vegvesen.nvdb.tnits.openlr.TillattRetning
import no.vegvesen.nvdb.tnits.openlr.toFormOfWay
import no.vegvesen.nvdb.tnits.storage.VeglenkerRepository
import no.vegvesen.nvdb.tnits.storage.VegobjekterRepository
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import no.vegvesen.nvdb.tnits.utilities.measure
import org.locationtech.jts.geom.Point
import org.openlr.map.FunctionalRoadClass
import java.util.concurrent.ConcurrentHashMap

class CachedVegnett(private val veglenkerRepository: VeglenkerRepository, private val vegobjekterRepository: VegobjekterRepository) : WithLogger {
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

    private var veglenkerInitialized = false

    private var initialized = false

    fun hasRetning(veglenke: Veglenke, retning: TillattRetning): Boolean {
        require(veglenkerInitialized)
        return retning in (tillattRetningByVeglenke[veglenke] ?: error("Mangler tillatt retning for veglenke ${veglenke.veglenkeId}"))
    }

    private val initMutex = Mutex()

    suspend fun initialize() {
        initMutex.withLock {
            if (initialized) return

            log.measure("Bygger vegnett-cache", logStart = true) {
                coroutineScope {
                    val veglenkerLoad = async {
                        log.measure("Load veglenker") { veglenkerRepository.getAll() }
                    }

                    val felstrekningerLoad = async {
                        log.measure("Load feltstrekninger") {
                            vegobjekterRepository.getVegobjektStedfestingLookup(VegobjektTyper.FELTSTREKNING)
                        }
                    }

                    val frcLoad = async {
                        log.measure("Load funksjonell vegklasse") {
                            vegobjekterRepository.getVegobjektStedfestingLookup(VegobjektTyper.FUNKSJONELL_VEGKLASSE)
                        }
                    }

                    veglenkerLookup = veglenkerLoad.await()
                    val feltstrekningerLookup = felstrekningerLoad.await()
                    val frcLookup = frcLoad.await()

                    veglenkerLookup.forEach { (_, veglenker) ->
                        launch {
                            veglenker.filter {
                                it.isRelevant()
                            }.forEach { veglenke ->

                                val feltoversikt = if (veglenke.konnektering) {
                                    findClosestNonKonnekteringVeglenke(veglenke, veglenker)?.feltoversikt ?: feltstrekningerLookup.findFeltoversikt(veglenke)
                                } else {
                                    veglenke.feltoversikt
                                }
                                if (feltoversikt.isNotEmpty()) {
                                    val frc = frcLookup.findFrc(veglenke)
                                    frcByVeglenke[veglenke] = frc
                                    addVeglenke(veglenke, feltoversikt)
                                } else {
                                    // Sannsynligvis gangveg uten fartsgrense
                                }
                            }
                        }
                    }
                }

                veglenkerInitialized = true

                log.measure("Load OpenLR lines") {
                    coroutineScope {
                        veglenkerLookup.forEach { (_, veglenker) ->
                            launch {
                                veglenker.filter {
                                    it.isRelevant()
                                }.forEach { veglenke ->
                                    val tillattRetning = tillattRetningByVeglenke[veglenke]
                                    if (tillattRetning != null) {
                                        if (TillattRetning.Med in tillattRetning) {
                                            linesByVeglenkerForward[veglenke] = createOpenLrLine(veglenke, TillattRetning.Med)
                                        }
                                        if (TillattRetning.Mot in tillattRetning) {
                                            linesByVeglenkerReverse[veglenke] = createOpenLrLine(veglenke, TillattRetning.Mot)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            initialized = true
        }
    }

    private fun addVeglenke(veglenke: Veglenke, feltoversikt: List<String>) {
        val tillattRetning = getTillattRetning(feltoversikt)

        tillattRetningByVeglenke[veglenke] = tillattRetning

        if (TillattRetning.Med in tillattRetning) {
            outgoingVeglenkerForward.computeIfAbsent(veglenke.startnode) { ConcurrentHashMap.newKeySet() }.add(veglenke)
            incomingVeglenkerForward.computeIfAbsent(veglenke.sluttnode) { ConcurrentHashMap.newKeySet() }.add(veglenke)
        }
        if (TillattRetning.Mot in tillattRetning) {
            outgoingVeglenkerReverse.computeIfAbsent(veglenke.sluttnode) { ConcurrentHashMap.newKeySet() }.add(veglenke)
            incomingVeglenkerReverse.computeIfAbsent(veglenke.startnode) { ConcurrentHashMap.newKeySet() }.add(veglenke)
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
        check(veglenkerInitialized) { "CachedVegnett is not initialized" }
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
        require(veglenkerInitialized)
        val outgoingVeglenker = when (retning) {
            TillattRetning.Med -> outgoingVeglenkerForward
            TillattRetning.Mot -> outgoingVeglenkerReverse
        }
        return outgoingVeglenker[nodeId] ?: emptySet()
    }

    fun getIncomingVeglenker(nodeId: Long, retning: TillattRetning): Set<Veglenke> {
        require(veglenkerInitialized)
        val incomingVeglenker = when (retning) {
            TillattRetning.Med -> incomingVeglenkerForward
            TillattRetning.Mot -> incomingVeglenkerReverse
        }
        return incomingVeglenker[nodeId] ?: emptySet()
    }

    fun getIncomingLines(nodeId: Long, retning: TillattRetning): List<OpenLrLine> {
        require(initialized)
        return getIncomingVeglenker(nodeId, retning).map { getLine(it, retning) }
    }

    fun getOutgoingLines(nodeId: Long, retning: TillattRetning): List<OpenLrLine> {
        require(initialized)
        return getOutgoingVeglenker(nodeId, retning).map { getLine(it, retning) }
    }

    fun getNode(nodeId: Long, retning: TillattRetning, getPoint: () -> Point): OpenLrNode {
        require(veglenkerInitialized)
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
        val linesByVeglenker = when (retning) {
            TillattRetning.Med -> linesByVeglenkerForward
            TillattRetning.Mot -> linesByVeglenkerReverse
        }
        return veglenker.map { veglenke ->
            linesByVeglenker[veglenke] ?: error("Veglenker $veglenke not found")
        }
    }

    fun getLine(veglenke: Veglenke, retning: TillattRetning): OpenLrLine {
        require(initialized)
        val linesByVeglenker = when (retning) {
            TillattRetning.Med -> linesByVeglenkerForward
            TillattRetning.Mot -> linesByVeglenkerReverse
        }
        return linesByVeglenker[veglenke] ?: error("Veglenker $veglenke not found")
    }

    private fun createOpenLrLine(veglenke: Veglenke, retning: TillattRetning): OpenLrLine {
        val frc = frcByVeglenke[veglenke] ?: FunctionalRoadClass.FRC_7
        val fow = veglenke.typeVeg.toFormOfWay()
        return OpenLrLine.fromVeglenke(veglenke, frc, fow, this, retning)
    }

    companion object {

        /**
         * Finn feltoversikt for veglenke ved å lete opp første feltstrekning med overlappende stedfesting.
         */
        private fun Map<Long, List<Vegobjekt>>.findFeltoversikt(veglenke: Veglenke): List<String> {
            val feltstrekning = this[veglenke.veglenkesekvensId]?.firstOrNull {
                it.stedfestinger.any { stedfesting -> stedfesting.overlaps(veglenke) }
            }
            if (feltstrekning == null) {
                // Sannsynligvis gangveg uten fartsgrense
                return emptyList()
            }
            val feltoversikt = (feltstrekning.egenskaper[EgenskapsTyper.FELTOVERSIKT_I_VEGLENKERETNING] as? TekstVerdi)?.verdi?.split(
                "#",
            )
            return feltoversikt
                ?: error("Finner ikke feltoversikt for veglenke ${veglenke.veglenkeId}")
        }

        /** Finn høyeste (laveste viktighet) funksjonell vegklasse for veglenke */
        private fun Map<Long, List<Vegobjekt>>.findFrc(veglenke: Veglenke): FunctionalRoadClass {
            val frc = this[veglenke.veglenkesekvensId]?.mapNotNull { it.egenskaper[EgenskapsTyper.VEGKLASSE] as? EnumVerdi }
                ?.maxByOrNull { it.verdi }?.toFrc()
            // Mange gangveger har ikke funksjonell vegklasse
            return frc ?: FunctionalRoadClass.FRC_7
        }
    }
}
