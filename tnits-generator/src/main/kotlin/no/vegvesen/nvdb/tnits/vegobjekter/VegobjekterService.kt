package no.vegvesen.nvdb.tnits.vegobjekter

import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import no.vegvesen.nvdb.apiles.uberiket.InkluderIVegobjekt
import no.vegvesen.nvdb.apiles.uberiket.StedfestingLinjer
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt
import no.vegvesen.nvdb.tnits.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.gateways.UberiketApi
import no.vegvesen.nvdb.tnits.model.*
import no.vegvesen.nvdb.tnits.storage.KeyValueRocksDbStore
import no.vegvesen.nvdb.tnits.storage.RocksDbContext
import no.vegvesen.nvdb.tnits.storage.VegobjekterRepository
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import kotlin.time.Clock
import kotlin.time.Instant

class VegobjekterService(
    private val keyValueStore: KeyValueRocksDbStore,
    private val uberiketApi: UberiketApi,
    private val vegobjekterRepository: VegobjekterRepository,
    private val rocksDbContext: RocksDbContext,
) : WithLogger {
    suspend fun backfillVegobjekter(typeId: Int, fetchOriginalStartDate: Boolean) {
        val backfillCompleted = keyValueStore.get<Instant>("vegobjekter_${typeId}_backfill_completed")

        if (backfillCompleted != null) {
            log.info("Backfill for type $typeId er allerede fullført den $backfillCompleted")
            return
        }

        var lastId = keyValueStore.get<Long>("vegobjekter_${typeId}_backfill_last_id")

        if (lastId == null) {
            log.info("Ingen backfill har blitt startet ennå for type $typeId. Starter backfill...")
            val now = Clock.System.now()
            keyValueStore.put("vegobjekter_${typeId}_backfill_started", now)
        } else {
            log.info("Backfill pågår for type $typeId. Gjenopptar fra siste ID: $lastId")
        }

        var totalCount = 0

        do {
            val vegobjekter = uberiketApi.streamVegobjekter(typeId = typeId, start = lastId).toList()
            lastId = vegobjekter.lastOrNull()?.id

            if (vegobjekter.isEmpty()) {
                log.info("Ingen vegobjekter å sette inn for type $typeId, backfill fullført.")
                keyValueStore.put("vegobjekter_${typeId}_backfill_completed", Clock.System.now())
            } else {
                val validFromById = if (fetchOriginalStartDate) getOriginalStartdatoWhereDifferent(typeId, vegobjekter) else emptyMap()

                rocksDbContext.writeBatch {
                    vegobjekterRepository.batchInsert(typeId, vegobjekter.toDomainVegobjekter(validFromById))
                    keyValueStore.put("vegobjekter_${typeId}_backfill_last_id", lastId!!)
                }
                totalCount += vegobjekter.size
                log.info("Satt inn ${vegobjekter.size} vegobjekter for type $typeId, totalt antall: $totalCount")
            }
        } while (vegobjekter.isNotEmpty())
    }

    private suspend fun getOriginalStartdatoWhereDifferent(typeId: Int, vegobjekter: Collection<Vegobjekt>): Map<Long, LocalDate> {
        val vegobjekterThatAreNotFirstVersion = vegobjekter.filter { it.versjon > 1 }.map { it.id }.toSet()
        val validFromById = uberiketApi.getVegobjekterPaginated(typeId, vegobjekterThatAreNotFirstVersion, setOf(InkluderIVegobjekt.GYLDIGHETSPERIODE))
            .toList().groupBy { it.id }.mapValues { it.value.first().gyldighetsperiode!!.startdato.toKotlinLocalDate() }
        return validFromById
    }

    data class VegobjektChanges(val changesById: MutableMap<Long, ChangeType>, val lastHendelseId: Long)

    suspend fun getVegobjektChanges(typeId: Int, lastHendelseId: Long): VegobjektChanges {
        val changesById = mutableMapOf<Long, ChangeType>()

        var start = lastHendelseId

        do {
            val response = uberiketApi.getVegobjektHendelser(typeId = typeId, start = start)

            val hendelser = response.hendelser

            start = hendelser.lastOrNull()?.hendelseId ?: start

            for (hendelse in hendelser) {
                when {
                    hendelse.hendelseType == "VegobjektImportert" -> changesById[hendelse.vegobjektId] = ChangeType.NEW
                    hendelse.hendelseType == "VegobjektVersjonOpprettet" && hendelse.vegobjektVersjon == 1 -> changesById[hendelse.vegobjektId] =
                        ChangeType.NEW

                    hendelse.hendelseType == "VegobjektVersjonFjernet" && hendelse.vegobjektVersjon == 1 -> changesById[hendelse.vegobjektId] =
                        ChangeType.DELETED

                    changesById[hendelse.vegobjektId] != ChangeType.NEW -> changesById[hendelse.vegobjektId] = ChangeType.MODIFIED
                }
            }
        } while (hendelser.isNotEmpty())

        return VegobjektChanges(changesById, start)
    }

    suspend fun updateVegobjekter(typeId: Int, fetchOriginalStartDate: Boolean): Int {
        val previousHendelseId = getLastHendelseId(typeId)

        log.info("Starter oppdatering av vegobjekter for type $typeId, siste hendelse-ID: $previousHendelseId")

        val (changesById, latestHendelseId) = getVegobjektChanges(typeId, previousHendelseId)

        if (changesById.isEmpty()) {
            log.info("Ingen endringer funnet for type $typeId siden hendelse-ID $previousHendelseId")
            return 0
        }

        val vegobjekterById = fetchVegobjekterByIds(typeId, changesById.keys)

        val validFromById = mutableMapOf<Long, LocalDate>()

        if (fetchOriginalStartDate) {
            getOriginalStartdatoWhereDifferent(typeId, vegobjekterById.values)
                .let(validFromById::plus)
        }

        val updatesById = changesById.mapValues { (id, changeType) ->
            if (changeType == ChangeType.DELETED) {
                VegobjektUpdate(id, changeType)
            } else {
                val apiVegobjekt = vegobjekterById[id]
                    ?: error("Forventet vegobjekt med ID $id for endringstype $changeType")
                val domainVegobjekt = apiVegobjekt.toDomain(validFromById[id])
                VegobjektUpdate(id, changeType, domainVegobjekt)
            }
        }

        rocksDbContext.writeBatch {
            vegobjekterRepository.batchUpdate(typeId, updatesById)
            keyValueStore.put("vegobjekter_${typeId}_last_hendelse_id", latestHendelseId)
        }

        return changesById.size
    }

    private val vegobjekterFetchSize = 100

    private suspend fun fetchVegobjekterByIds(typeId: Int, ids: Collection<Long>): MutableMap<Long, Vegobjekt> {
        val vegobjekterById = mutableMapOf<Long, Vegobjekt>()

        ids.forEachChunked(vegobjekterFetchSize) { ids ->
            uberiketApi.streamVegobjekter(typeId = typeId, ider = ids).collect {
                vegobjekterById[it.id] = it
            }
        }
        return vegobjekterById
    }

    private suspend fun getLastHendelseId(typeId: Int): Long =
        keyValueStore.get<Long>("vegobjekter_${typeId}_last_hendelse_id") ?: uberiketApi.getLatestVegobjektHendelseId(
            typeId,
            keyValueStore.get<Instant>("vegobjekter_${typeId}_backfill_completed")
                ?: error("Backfill for type $typeId er ikke ferdig"),
        )
}

fun Vegobjekt.getStedfestingLinjer(): List<VegobjektStedfesting> = when (val stedfesting = this.stedfesting) {
    is StedfestingLinjer ->
        stedfesting.linjer.map {
            VegobjektStedfesting(
                veglenkesekvensId = it.id,
                startposisjon = it.startposisjon,
                sluttposisjon = it.sluttposisjon,
                retning = it.retning,
                sideposisjon = it.sideposisjon,
                kjorefelt = it.kjorefelt,
            )
        }

    else -> error("Forventet StedfestingLinjer, fikk ${stedfesting::class.simpleName}")
}
