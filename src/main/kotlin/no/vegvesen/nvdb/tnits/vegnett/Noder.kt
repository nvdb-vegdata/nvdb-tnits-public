package no.vegvesen.nvdb.tnits.vegnett

import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.extensions.get
import no.vegvesen.nvdb.tnits.extensions.put
import no.vegvesen.nvdb.tnits.measure
import no.vegvesen.nvdb.tnits.nodePortCountRepository
import no.vegvesen.nvdb.tnits.uberiketApi
import kotlin.time.Clock
import kotlin.time.Instant

suspend fun backfillNoder() {
    var lastId = KeyValue.get<Long>("noder_backfill_last_id")

    if (lastId == null) {
        println("Ingen noder backfill har blitt startet ennå. Starter backfill...")
        val now = Clock.System.now()
        KeyValue.put("noder_backfill_started", now)
    } else {
        println("Noder backfill pågår. Gjenopptar fra siste ID: $lastId")
    }

    var totalCount = 0
    val updates = mutableMapOf<Long, Int>()

    do {
        val noderSide = uberiketApi.getNoder(start = lastId)
        val noder = noderSide.noder
        lastId = noder.lastOrNull()?.id

        if (noder.isEmpty()) {
            println("Ingen noder å sette inn, backfill fullført.")
            KeyValue.put("noder_backfill_completed", Clock.System.now())
        } else {
            measure("Behandler ${noder.size} noder") {
                noder.forEach { node ->
                    val portCount = node.porter.size
                    updates[node.id] = portCount
                }

                nodePortCountRepository.batchUpdate(updates)
                updates.clear()

                KeyValue.put("noder_backfill_last_id", lastId!!)
            }

            totalCount += noder.size
            println("Behandlet ${noder.size} noder, totalt antall: $totalCount")
        }
    } while (noder.isNotEmpty())
}

suspend fun updateNoder() {
    var lastHendelseId =
        KeyValue.get<Long>("noder_last_hendelse_id") ?: uberiketApi.getLatestNodeHendelseId(
            KeyValue.get<Instant>("noder_backfill_completed")
                ?: error("Noder backfill er ikke ferdig"),
        )

    do {
        val response =
            uberiketApi.getNodeHendelser(
                start = lastHendelseId,
            )

        if (response.hendelser.isNotEmpty()) {
            lastHendelseId = response.hendelser.last().hendelseId
            val changedIds = response.hendelser.map { it.nettelementId }.toSet()
            val updates = mutableMapOf<Long, Int?>()

            // Process changed noder in chunks
            changedIds.forEachChunked(100) { chunk ->
                var start: Long? = null
                do {
                    val noderSide = uberiketApi.getNoder(start = start, ider = chunk)
                    val noder = noderSide.noder

                    if (noder.isNotEmpty()) {
                        noder.forEach { node ->
                            val portCount = node.porter.size
                            updates[node.id] = portCount
                        }
                        start = noder.maxOfOrNull { it.id }?.plus(1)
                    }
                } while (noder.isNotEmpty())
            }

            // Handle deleted noder (those that didn't return data)
            val foundIds = updates.keys
            val deletedIds = changedIds - foundIds
            deletedIds.forEach { deletedId ->
                updates[deletedId] = null // Mark for deletion
            }

            // Apply all updates to RocksDB
            nodePortCountRepository.batchUpdate(updates)

            // Update progress tracking
            KeyValue.put("noder_last_hendelse_id", lastHendelseId)

            println("Behandlet ${response.hendelser.size} noder-hendelser, siste ID: $lastHendelseId")
        }
    } while (response.hendelser.isNotEmpty())
    println("Oppdatering av noder fullført. Siste hendelse-ID: $lastHendelseId")
}
