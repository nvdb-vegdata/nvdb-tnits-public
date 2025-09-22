package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.utilities.measure
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Instant

val mainVegobjektTyper = listOf(VegobjektTyper.FARTSGRENSE)

val supportingVegobjektTyper = setOf(VegobjektTyper.FELTSTREKNING, VegobjektTyper.FUNKSJONELL_VEGKLASSE)

val log: Logger = LoggerFactory.getLogger("tnits")

suspend fun main() {
    log.info("Starter NVDB TN-ITS konsollapplikasjon...")

    with(Services()) {
        performRestoreIfNeeded()
        performBackfill()

        val now: Instant = performUpdateAndGetTimestamp()

        log.info("Oppdateringer fullført!")

        log.measure("Bygger vegnett-cache", true) {
            cachedVegnett.initialize()
        }

        handleInput(now)
    }
}

private suspend fun Services.handleInput(now: Instant) {
    do {
        println("Trykk:")
        println(" 1 for å generere fullt snapshot av TN-ITS fartsgrenser")
        println(" 2 for å generere delta snapshot")
        println(" 3 for å ta backup av RocksDB til S3")
        println(" 4 for å avslutte")

        val input: String = readln().trim()

        when (input) {
            "1" -> {
                exportSpeedLimitsSnapshot(now)
            }

            "2" -> {
                exportSpeedLimitsUpdate(now)
            }

            "3" -> {
                log.info("Tar backup av RocksDB...")
                val success = rocksDbBackupService.createBackup()
                if (success) {
                    log.info("RocksDB backup fullført")
                } else {
                    log.error("RocksDB backup feilet")
                }
            }

            "4" -> {
                log.info("Avslutter applikasjonen...")
                return
            }

            else -> log.info("Ugyldig valg, vennligst prøv igjen.")
        }
    } while (true)
}

private suspend fun Services.exportSpeedLimitsUpdate(now: Instant) {
    log.info("Genererer delta snapshot av TN-ITS fartsgrenser...")
    val since = s3TimestampService.getLastUpdateTimestamp()
        ?: s3TimestampService.getLastSnapshotTimestamp()
        ?: error("Ingen tidligere snapshot eller oppdateringstidspunkt funnet for fartsgrenser")

    tnitsFeatureExporter.generateSpeedLimitsDeltaUpdate(now, since)
}

private suspend fun Services.exportSpeedLimitsSnapshot(now: Instant) {
    log.info("Genererer fullt snapshot av TN-ITS fartsgrenser...")
    tnitsFeatureExporter.exportSpeedLimitsFullSnapshot(now)
}

private suspend fun Services.performBackfill() {
    coroutineScope {
        launch {
            log.info("Oppdaterer veglenkesekvenser...")
            veglenkesekvenserService.backfillVeglenkesekvenser()
        }

        mainVegobjektTyper.forEach { typeId ->
            launch {
                log.info("Oppdaterer vegobjekter for hovedtype $typeId...")
                vegobjekterService.backfillVegobjekter(typeId, true)
            }
        }
        supportingVegobjektTyper.forEach { typeId ->
            launch {
                log.info("Oppdaterer vegobjekter for støttende type $typeId...")
                vegobjekterService.backfillVegobjekter(typeId, false)
            }
        }
    }
}

private suspend fun Services.performUpdateAndGetTimestamp(): Instant {
    var now: Instant
    coroutineScope {
        do {
            now = Clock.System.now()
            val veglenkesekvensHendelseCount =
                async {
                    veglenkesekvenserService.updateVeglenkesekvenser()
                }
            val vegobjekterHendelseCounts =
                mainVegobjektTyper.map { typeId ->
                    async {
                        vegobjekterService.updateVegobjekter(typeId, true)
                    }
                }
            val vegobjekterSupportingHendelseCounts =
                supportingVegobjektTyper.map { typeId ->
                    async {
                        vegobjekterService.updateVegobjekter(typeId, false)
                    }
                }

            val total =
                veglenkesekvensHendelseCount.await() + vegobjekterHendelseCounts.sumOf { it.await() } + vegobjekterSupportingHendelseCounts.sumOf { it.await() }
        } while (total > 0)
    }
    return now
}

private suspend fun Services.performRestoreIfNeeded() {
    try {
        if (!rocksDbContext.existsAndHasData()) {
            log.info("RocksDB database is empty or missing, checking for backup to restore...")
            val restored = rocksDbBackupService.restoreFromBackup()
            if (restored) {
                log.info("Successfully restored RocksDB from backup")
            } else {
                log.info("No backup available or restore failed, will proceed with full backfill")
            }
        } else {
            log.info("RocksDB database exists and has data, skipping restore")
        }
    } catch (e: Exception) {
        log.warn("Error during restore check, will proceed with full backfill", e)
    }
}
