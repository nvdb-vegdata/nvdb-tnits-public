package no.vegvesen.nvdb.tnits.generator.core.services.vegnett

import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.tnits.common.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.generator.core.api.VeglenkerRepository
import no.vegvesen.nvdb.tnits.generator.core.api.VegobjekterRepository
import no.vegvesen.nvdb.tnits.generator.core.extensions.WithLogger
import no.vegvesen.nvdb.tnits.generator.core.extensions.logMemoryUsage
import no.vegvesen.nvdb.tnits.generator.core.extensions.measure
import no.vegvesen.nvdb.tnits.generator.core.extensions.today
import no.vegvesen.nvdb.tnits.generator.core.model.*
import org.locationtech.jts.geom.Point
import org.openlr.map.FunctionalRoadClass
import java.util.concurrent.ConcurrentHashMap

@Singleton
class CachedVegnett(private val veglenkerRepository: VeglenkerRepository, private val vegobjekterRepository: VegobjekterRepository) :
    WithLogger {
    private lateinit var veglenkerLookup: Map<Long, List<Veglenke>>
    private var outgoingVeglenkerForward: MutableMap<Long, MutableSet<VeglenkeId>> = ConcurrentHashMap()
    private var incomingVeglenkerForward: MutableMap<Long, MutableSet<VeglenkeId>> = ConcurrentHashMap()
    private var outgoingVeglenkerReverse: MutableMap<Long, MutableSet<VeglenkeId>> = ConcurrentHashMap()
    private var incomingVeglenkerReverse: MutableMap<Long, MutableSet<VeglenkeId>> = ConcurrentHashMap()

    private var tillattRetningByVeglenke: MutableMap<VeglenkeId, Byte> = ConcurrentHashMap()

    private var frcByVeglenke: MutableMap<VeglenkeId, Byte> = ConcurrentHashMap()

    private val nodes = ConcurrentHashMap<Long, OpenLrNode>()

    private val lineCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .build<Pair<Veglenke, TillattRetning>, OpenLrLine>()

    private var veglenkerInitialized = false

    private var initialized = false

    fun hasRetning(veglenke: Veglenke, retning: TillattRetning): Boolean {
        require(veglenkerInitialized)
        val retningByte = tillattRetningByVeglenke[veglenke.veglenkeId] ?: error("Mangler tillatt retning for veglenke ${veglenke.veglenkeId}")
        return retning in retningByte.toTillattRetning()
    }

    fun getFrc(veglenke: Veglenke): FunctionalRoadClass? {
        require(veglenkerInitialized)
        return frcByVeglenke[veglenke.veglenkeId]?.toFunctionalRoadClass()
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

                    log.measure("Assign FRC and feltoversikt to veglenker", logStart = true) {
                        veglenkerLookup.forEach { (_, veglenker) ->
                            veglenker.filter {
                                it.isRelevant()
                            }.forEach { veglenke ->

                                val feltoversikt = if (veglenke.konnektering) {
                                    findClosestNonKonnekteringVeglenke(veglenke, veglenker)?.feltoversikt
                                        ?: feltstrekningerLookup.findFeltoversikt(veglenke)
                                } else {
                                    veglenke.feltoversikt
                                }
                                if (feltoversikt.isNotEmpty()) {
                                    val frc = frcLookup.findFrc(veglenke)
                                    frcByVeglenke[veglenke.veglenkeId] = frc.toByte()
                                    addVeglenke(veglenke, feltoversikt)
                                } else {
                                    // Sannsynligvis gangveg uten fartsgrense
                                }
                            }
                        }
                    }
                }

                veglenkerInitialized = true

                log.measure("Convert to immutable HashMaps") {
                    outgoingVeglenkerForward = HashMap(outgoingVeglenkerForward)
                    incomingVeglenkerForward = HashMap(incomingVeglenkerForward)
                    outgoingVeglenkerReverse = HashMap(outgoingVeglenkerReverse)
                    incomingVeglenkerReverse = HashMap(incomingVeglenkerReverse)
                    tillattRetningByVeglenke = HashMap(tillattRetningByVeglenke)
                    frcByVeglenke = HashMap(frcByVeglenke)
                }

                // Force garbage collection after a large memory allocation
                System.gc()

                log.logMemoryUsage("After veglenker initialization and HashMap conversion")
            }

            initialized = true
        }
    }

    private fun addVeglenke(veglenke: Veglenke, feltoversikt: List<String>) {
        val tillattRetning = getTillattRetning(feltoversikt)

        tillattRetningByVeglenke[veglenke.veglenkeId] = tillattRetning.toByte()

        if (TillattRetning.Med in tillattRetning) {
            outgoingVeglenkerForward.computeIfAbsent(veglenke.startnode) { ConcurrentHashMap.newKeySet() }.add(veglenke.veglenkeId)
            incomingVeglenkerForward.computeIfAbsent(veglenke.sluttnode) { ConcurrentHashMap.newKeySet() }.add(veglenke.veglenkeId)
        }
        if (TillattRetning.Mot in tillattRetning) {
            outgoingVeglenkerReverse.computeIfAbsent(veglenke.sluttnode) { ConcurrentHashMap.newKeySet() }.add(veglenke.veglenkeId)
            incomingVeglenkerReverse.computeIfAbsent(veglenke.startnode) { ConcurrentHashMap.newKeySet() }.add(veglenke.veglenkeId)
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

    fun getAllVeglenker(): Map<Long, List<Veglenke>> {
        check(veglenkerInitialized) { "CachedVegnett is not initialized" }
        return veglenkerLookup
    }

    fun Veglenke.isRelevant(): Boolean = isTopLevel && typeVeg in setOf(
        TypeVeg.KANALISERT_VEG,
        TypeVeg.ENKEL_BILVEG,
        TypeVeg.GATETUN,
        TypeVeg.RAMPE,
        TypeVeg.RUNDKJORING,
        TypeVeg.TRAKTORVEG,
        TypeVeg.GAGATE,
        TypeVeg.GANG_OG_SYKKELVEG,
        TypeVeg.GANGVEG,
    ) && startdato <= today && (sluttdato == null || sluttdato > today)

    fun getOutgoingVeglenker(nodeId: Long, retning: TillattRetning): Set<Veglenke> {
        require(veglenkerInitialized)
        val outgoingVeglenker = when (retning) {
            TillattRetning.Med -> outgoingVeglenkerForward
            TillattRetning.Mot -> outgoingVeglenkerReverse
        }
        val veglenkeIds = outgoingVeglenker[nodeId] ?: emptySet()
        return veglenkeIds.mapNotNull { getVeglenkeById(it) }.toSet()
    }

    fun getIncomingVeglenker(nodeId: Long, retning: TillattRetning): Set<Veglenke> {
        require(veglenkerInitialized)
        val incomingVeglenker = when (retning) {
            TillattRetning.Med -> incomingVeglenkerForward
            TillattRetning.Mot -> incomingVeglenkerReverse
        }
        val veglenkeIds = incomingVeglenker[nodeId] ?: emptySet()
        return veglenkeIds.mapNotNull { getVeglenkeById(it) }.toSet()
    }

    private fun getVeglenkeById(veglenkeId: VeglenkeId): Veglenke? = veglenkerLookup[veglenkeId.veglenkesekvensId]?.firstOrNull {
        it.veglenkeId == veglenkeId
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
        require(veglenkerInitialized)
        return veglenker.map { veglenke ->
            getLine(veglenke, retning)
        }
    }

    fun getLine(veglenke: Veglenke, retning: TillattRetning): OpenLrLine {
        require(veglenkerInitialized)
        return lineCache.get(veglenke to retning) {
            createOpenLrLine(veglenke, retning)
        }
    }

    private fun createOpenLrLine(veglenke: Veglenke, retning: TillattRetning): OpenLrLine {
        val frc = frcByVeglenke[veglenke.veglenkeId]?.toFunctionalRoadClass() ?: FunctionalRoadClass.FRC_7
        val fow = veglenke.typeVeg.toFormOfWay()
        return OpenLrLine.fromVeglenke(veglenke, frc, fow, this, retning)
    }

    private fun Byte.toFunctionalRoadClass(): FunctionalRoadClass = when (this.toInt()) {
        0 -> FunctionalRoadClass.FRC_0
        1 -> FunctionalRoadClass.FRC_1
        2 -> FunctionalRoadClass.FRC_2
        3 -> FunctionalRoadClass.FRC_3
        4 -> FunctionalRoadClass.FRC_4
        5 -> FunctionalRoadClass.FRC_5
        6 -> FunctionalRoadClass.FRC_6
        else -> FunctionalRoadClass.FRC_7
    }

    private fun FunctionalRoadClass.toByte(): Byte = when (this) {
        FunctionalRoadClass.FRC_0 -> 0
        FunctionalRoadClass.FRC_1 -> 1
        FunctionalRoadClass.FRC_2 -> 2
        FunctionalRoadClass.FRC_3 -> 3
        FunctionalRoadClass.FRC_4 -> 4
        FunctionalRoadClass.FRC_5 -> 5
        FunctionalRoadClass.FRC_6 -> 6
        FunctionalRoadClass.FRC_7 -> 7
    }

    private fun Set<TillattRetning>.toByte(): Byte = when {
        TillattRetning.Med in this && TillattRetning.Mot in this -> RETNING_BOTH
        TillattRetning.Med in this -> RETNING_MED
        TillattRetning.Mot in this -> RETNING_MOT
        else -> 0
    }

    private fun Byte.toTillattRetning(): Set<TillattRetning> = when (this) {
        RETNING_BOTH -> setOf(TillattRetning.Med, TillattRetning.Mot)
        RETNING_MED -> setOf(TillattRetning.Med)
        RETNING_MOT -> setOf(TillattRetning.Mot)
        else -> emptySet()
    }

    companion object {
        private const val RETNING_MED: Byte = 0b01
        private const val RETNING_MOT: Byte = 0b10
        private const val RETNING_BOTH: Byte = 0b11

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

        private fun EnumVerdi.toFrc() = when (verdi) {
            13060 -> FunctionalRoadClass.FRC_0
            13061 -> FunctionalRoadClass.FRC_1
            13062 -> FunctionalRoadClass.FRC_2
            13063 -> FunctionalRoadClass.FRC_3
            13064 -> FunctionalRoadClass.FRC_4
            13065 -> FunctionalRoadClass.FRC_5
            13066 -> FunctionalRoadClass.FRC_6
            else -> FunctionalRoadClass.FRC_7
        }
    }
}
