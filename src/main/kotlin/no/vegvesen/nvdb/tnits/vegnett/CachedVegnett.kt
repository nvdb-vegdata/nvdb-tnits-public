package no.vegvesen.nvdb.tnits.vegnett

import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.storage.VeglenkerRepository
import java.util.concurrent.ConcurrentHashMap

class CachedVegnett(
    private val veglenkerRepository: VeglenkerRepository,
) {
    private lateinit var veglenker: Map<Long, List<Veglenke>>
    private val outgoingVeglenker = ConcurrentHashMap<Long, MutableSet<Veglenke>>()
    private val incomingVeglenker = ConcurrentHashMap<Long, MutableSet<Veglenke>>()

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
}
