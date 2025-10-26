package no.vegvesen.nvdb.tnits.generator.infrastructure.ingest

import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.builtins.serializer
import no.vegvesen.nvdb.apiles.uberiket.InkluderIVegobjekt
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.generator.core.api.KeyValueStore
import no.vegvesen.nvdb.tnits.generator.core.api.UberiketApi
import no.vegvesen.nvdb.tnits.generator.core.api.VegobjekterRepository
import no.vegvesen.nvdb.tnits.generator.core.api.getValue
import no.vegvesen.nvdb.tnits.generator.core.model.*
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.RocksDbContext
import kotlin.time.Clock
import kotlin.time.Instant
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

@Singleton
class VegobjektLoader(
    private val keyValueStore: KeyValueStore,
    private val uberiketApi: UberiketApi,
    private val vegobjekterRepository: VegobjekterRepository,
    private val rocksDbContext: RocksDbContext,
    private val clock: Clock,
) : WithLogger {
    suspend fun backfillVegobjekter(typeId: Int, fetchOriginalStartDate: Boolean): Int {
        val backfillCompleted = keyValueStore.getValue<Instant>("vegobjekter_${typeId}_backfill_completed")

        if (backfillCompleted != null) {
            log.info("Backfill for type $typeId er allerede fullført den $backfillCompleted")
            return 0
        }

        var lastId = keyValueStore.getValue<Long>("vegobjekter_${typeId}_backfill_last_id")

        if (lastId == null) {
            log.info("Ingen backfill har blitt startet ennå for type $typeId. Starter backfill...")
            val now = clock.now()
            keyValueStore.put("vegobjekter_${typeId}_backfill_started", now, Instant.serializer())
        } else {
            log.info("Backfill pågår for type $typeId. Gjenopptar fra siste ID: $lastId")
        }

        var totalCount = 0
        var batchCount = 0

        do {
            val vegobjekter = uberiketApi.streamVegobjekter(typeId = typeId, start = lastId).toList()
            lastId = vegobjekter.lastOrNull()?.id

            if (vegobjekter.isEmpty()) {
                log.info("Ingen vegobjekter å sette inn for type $typeId, backfill fullført.")
                keyValueStore.put("vegobjekter_${typeId}_backfill_completed", clock.now(), Instant.serializer())
            } else {
                val validFromById = if (fetchOriginalStartDate) getOriginalStartdatoWhereDifferent(typeId, vegobjekter) else emptyMap()

                rocksDbContext.writeBatch {
                    vegobjekterRepository.batchInsert(typeId, vegobjekter.toDomainVegobjekter(validFromById))
                    keyValueStore.put("vegobjekter_${typeId}_backfill_last_id", lastId!!, Long.serializer())
                }
                totalCount += vegobjekter.size
                batchCount++
                if (batchCount % 50 == 0) {
                    log.info("Lastet $totalCount vegobjekter for type $typeId")
                }
            }
        } while (vegobjekter.isNotEmpty())

        return totalCount
    }

    private suspend fun getOriginalStartdatoWhereDifferent(typeId: Int, vegobjekter: Collection<ApiVegobjekt>): Map<Long, LocalDate> {
        val vegobjekterThatAreNotFirstVersion = vegobjekter.filter { it.versjon > 1 }.map { it.id }.toSet()
        val validFromById = uberiketApi.getVegobjekterPaginated(typeId, vegobjekterThatAreNotFirstVersion, setOf(InkluderIVegobjekt.GYLDIGHETSPERIODE))
            .toList().groupBy { it.id }
            // Sorted by version from API, so first() is the lowest version
            .mapValues { it.value.first().gyldighetsperiode!!.startdato.toKotlinLocalDate() }
        return validFromById
    }

    data class VegobjektChanges(val changesById: MutableMap<Long, ChangeType>, val lastHendelseId: Long)

    suspend fun getVegobjektChanges(typeId: Int, lastHendelseId: Long): VegobjektChanges {
        val changesById = mutableMapOf<Long, ChangeType>()

        var latestHendelseId = lastHendelseId

        uberiketApi.streamVegobjektHendelser(typeId = typeId, start = latestHendelseId).collect { hendelse ->
            latestHendelseId = hendelse.hendelseId
            when {
                hendelse.hendelseType == "VegobjektImportert" -> changesById[hendelse.vegobjektId] = ChangeType.NEW
                hendelse.hendelseType == "VegobjektVersjonOpprettet" && hendelse.vegobjektVersjon == 1 -> changesById[hendelse.vegobjektId] =
                    ChangeType.NEW

                hendelse.hendelseType == "VegobjektVersjonFjernet" && hendelse.vegobjektVersjon == 1 -> changesById[hendelse.vegobjektId] =
                    ChangeType.DELETED

                changesById[hendelse.vegobjektId] != ChangeType.NEW -> changesById[hendelse.vegobjektId] = ChangeType.MODIFIED
            }
        }

        return VegobjektChanges(changesById, latestHendelseId)
    }

    suspend fun updateVegobjekter(typeId: Int, fetchOriginalStartDate: Boolean): Int {
        val previousHendelseId = getLastHendelseId(typeId)

        log.info("Starter oppdatering av vegobjekter for type $typeId, siste hendelse-ID: $previousHendelseId")

        val (changesById, latestHendelseId) = getVegobjektChanges(typeId, previousHendelseId)

        if (changesById.isEmpty()) {
            log.info("Ingen endringer funnet for type $typeId siden hendelse-ID $previousHendelseId")
            return 0
        }

        val vegobjekterById = fetchVegobjekterByIds(typeId, changesById.keys, fetchOriginalStartDate)

        val updatesById = changesById.mapValues { (id, changeType) ->
            if (changeType == ChangeType.DELETED) {
                VegobjektUpdate(id, changeType)
            } else {
                val vegobjekt = vegobjekterById[id]
                    ?: error("Forventet vegobjekt med ID $id for endringstype $changeType")
                VegobjektUpdate(id, changeType, vegobjekt)
            }
        }

        rocksDbContext.writeBatch {
            vegobjekterRepository.batchUpdate(typeId, updatesById)
            keyValueStore.put("vegobjekter_${typeId}_last_hendelse_id", latestHendelseId, Long.serializer())
        }

        return changesById.size
    }

    private suspend fun fetchVegobjekterByIds(typeId: Int, ids: Set<Long>, fetchOriginalStartDate: Boolean): Map<Long, Vegobjekt> =
        uberiketApi.getVegobjekterPaginated(typeId, ids, setOf(InkluderIVegobjekt.ALLE)).toList()
            .groupBy { it.id }
            .mapNotNull { (id, versjoner) ->
                val latest = versjoner.maxBy { it.versjon }
                val originalStartDate = if (fetchOriginalStartDate) {
                    val first = versjoner.minBy { it.versjon }
                    if (latest != first) {
                        first.gyldighetsperiode!!.startdato.toKotlinLocalDate()
                    } else {
                        null
                    }
                } else {
                    null
                }

                id to latest.toDomain(originalStartDate)
            }.toMap()

    private suspend fun getLastHendelseId(typeId: Int): Long =
        keyValueStore.getValue<Long>("vegobjekter_${typeId}_last_hendelse_id") ?: uberiketApi.getLatestVegobjektHendelseId(
            typeId,
            keyValueStore.getValue<Instant>("vegobjekter_${typeId}_backfill_completed")
                ?: error("Backfill for type $typeId er ikke ferdig"),
        )
}
