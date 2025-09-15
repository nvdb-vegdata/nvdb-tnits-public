package no.vegvesen.nvdb.tnits.vegobjekter

import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import no.vegvesen.nvdb.apiles.uberiket.InkluderIVegobjekt
import no.vegvesen.nvdb.apiles.uberiket.StedfestingLinjer
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt
import no.vegvesen.nvdb.tnits.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.model.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.model.toDomainVegobjektUpdates
import no.vegvesen.nvdb.tnits.model.toDomainVegobjekter
import no.vegvesen.nvdb.tnits.services.UberiketApi
import no.vegvesen.nvdb.tnits.storage.KeyValueRocksDbStore
import no.vegvesen.nvdb.tnits.storage.RocksDbContext
import no.vegvesen.nvdb.tnits.storage.VegobjekterRepository
import no.vegvesen.nvdb.tnits.supportingVegobjektTyper
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import kotlin.time.Clock
import kotlin.time.Instant

private val dirtyCheckForTypes = supportingVegobjektTyper

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

    private suspend fun getOriginalStartdatoWhereDifferent(typeId: Int, vegobjekter: List<Vegobjekt>): Map<Long, LocalDate> {
        val vegobjekterThatAreNotFirstVersion = vegobjekter.filter { it.versjon > 1 }.map { it.id }.toSet()
        val validFromById = uberiketApi.getVegobjekterPaginated(typeId, vegobjekterThatAreNotFirstVersion, setOf(InkluderIVegobjekt.GYLDIGHETSPERIODE))
            .toList().groupBy { it.id }.mapValues { it.value.first().gyldighetsperiode!!.startdato.toKotlinLocalDate() }
        return validFromById
    }

    suspend fun updateVegobjekter(typeId: Int, fetchOriginalStartDate: Boolean): Int {
        var lastHendelseId =
            keyValueStore.get<Long>("vegobjekter_${typeId}_last_hendelse_id") ?: uberiketApi.getLatestVegobjektHendelseId(
                typeId,
                keyValueStore.get<Instant>("vegobjekter_${typeId}_backfill_completed")
                    ?: error("Backfill for type $typeId er ikke ferdig"),
            )

        var hendelseCount = 0

        log.info("Starter oppdatering av vegobjekter for type $typeId, siste hendelse-ID: $lastHendelseId")

        do {
            val response =
                uberiketApi.getVegobjektHendelser(
                    typeId = typeId,
                    start = lastHendelseId,
                )

            if (response.hendelser.isNotEmpty()) {
                lastHendelseId = response.hendelser.last().hendelseId
                val changedIds = response.hendelser.map { it.vegobjektId }.toSet()
                val vegobjekter = mutableMapOf<Long, Vegobjekt?>()

                val validFromById = mutableMapOf<Long, LocalDate>()

                changedIds.forEachChunked(100) { chunk ->
                    var start: Long? = null
                    do {
                        val batch = uberiketApi.streamVegobjekter(typeId = typeId, start = start, ider = chunk).toList()

                        if (batch.isNotEmpty()) {
                            for (vegobjekt in batch) {
                                vegobjekter[vegobjekt.id] = vegobjekt
                            }
                            if (fetchOriginalStartDate) {
                                getOriginalStartdatoWhereDifferent(typeId, batch)
                                    .let(validFromById::plus)
                            }
                            start = batch.last().id
                        }
                    } while (batch.isNotEmpty())
                    for (id in chunk.filter { it !in vegobjekter }) {
                        vegobjekter[id] = null // Deleted
                    }
                }

                rocksDbContext.writeBatch {
                    vegobjekterRepository.batchUpdate(typeId, vegobjekter.toDomainVegobjektUpdates(validFromById))
                    keyValueStore.put("vegobjekter_${typeId}_last_hendelse_id", lastHendelseId)
                }
                log.info("Behandlet ${response.hendelser.size} hendelser for type $typeId, siste ID: $lastHendelseId")
                hendelseCount += response.hendelser.size
            }
        } while (response.hendelser.isNotEmpty())
        log.info("Oppdatering av vegobjekter type $typeId fullført. Siste hendelse-ID: $lastHendelseId")
        return hendelseCount
    }
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
