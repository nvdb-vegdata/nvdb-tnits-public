package no.vegvesen.nvdb.tnits

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.tnits.Services.Companion.withServices
import no.vegvesen.nvdb.tnits.extensions.toOffsetDateTime
import no.vegvesen.nvdb.tnits.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.utilities.measure
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Instant

val mainVegobjektTyper = listOf(VegobjektTyper.FARTSGRENSE)

val supportingVegobjektTyper = setOf(VegobjektTyper.FELTSTREKNING, VegobjektTyper.FUNKSJONELL_VEGKLASSE)

val log: Logger = LoggerFactory.getLogger("tnits")

suspend fun main(args: Array<String>) {
    log.info("Starting NVDB TN-ITS application on process ${ProcessHandle.current().pid()}")
    NvdbTnitsApp
        .subcommands(SnapshotCommand, UpdateCommand, BackupCommand, AutoCommand)
        .main(args)
    log.info("NVDB TN-ITS application finished")
}

object NvdbTnitsApp : SuspendingCliktCommand() {
    override suspend fun run() {
        // This method is called for subcommands, but we don't want to print anything here
        // The help is handled automatically by Clikt when no subcommand is provided
    }
}

abstract class BaseCommand : SuspendingCliktCommand() {
    protected val noBackup by option("--no-backup")
        .flag()
        .help("Ikke ta automatisk backup etter vellykket operasjon")

    protected fun Services.performBackupIfNeeded() {
        if (!noBackup) {
            log.info("Tar automatisk backup av RocksDB...")
            rocksDbBackupService.createBackup()
        }
    }
}

private suspend fun Services.synchronizeVegobjekterAndVegnett(): Instant {
    performRestoreIfNeeded()
    performBackfill()

    val now: Instant = performUpdateAndGetTimestamp()

    log.info("Oppdateringer fullført!")

    log.measure("Bygger vegnett-cache", true) {
        cachedVegnett.initialize()
    }
    return now
}

object SnapshotCommand : BaseCommand() {
    override suspend fun run() {
        withServices {
            val now = synchronizeVegobjekterAndVegnett()
            tnitsFeatureExporter.exportSnapshot(now, ExportedFeatureType.SpeedLimit)
            performBackupIfNeeded()
        }
    }
}

object UpdateCommand : BaseCommand() {
    override suspend fun run() {
        withServices {
            val now = synchronizeVegobjekterAndVegnett()
            exportUpdate(now, ExportedFeatureType.SpeedLimit)
            performBackupIfNeeded()
        }
    }
}

object BackupCommand : BaseCommand() {
    override suspend fun run() {
        withServices {
            synchronizeVegobjekterAndVegnett()
            rocksDbBackupService.createBackup()
        }
    }
}

object AutoCommand : BaseCommand() {
    override suspend fun run() {
        echo("TODO: Implementer automatisk modus basert på konfigurasjon")
        echo("Denne modusen vil sjekke konfigurasjon for å avgjøre om det skal:")
        echo("- Kjøre fullt snapshot")
        echo("- Kjøre delta oppdatering")
        echo("- Begge deler")
    }
}

private suspend fun Services.getChangedVegobjektIds(typeId: Int, since: Instant, until: Instant): Set<Long> {
    val untilOffset = until.toOffsetDateTime()
    // TODO: Vi håndterer bare direkte endringer, ikke endringer som følge av endringer i vegnett eller FRC
    return uberiketApi.getVegobjektHendelserPaginated(typeId, since)
        .takeWhile { it.tidspunkt < untilOffset }
        .map { it.vegobjektId }
        .toSet()
}

private suspend fun Services.exportUpdate(now: Instant, featureType: ExportedFeatureType) {
    log.info("Genererer delta snapshot av TN-ITS ${featureType.typeCode}...")
    val vegobjektChanges = dirtyCheckingRepository.getDirtyVegobjektChanges(featureType.typeId)

    if (vegobjektChanges.isEmpty()) {
        log.info("Ingen endringer siden forrige eksport, hopper over eksport")
        return
    }

    log.info("Eksporterer ${vegobjektChanges.size} endrede vegobjekter for TN-ITS ${featureType.typeCode}...")

    // TODO: Handle if we lose dirty checking data (e.g., after a crash), by checking timestamp for latest export, and fetching hendelser

    tnitsFeatureExporter.exportUpdate(now, featureType, vegobjektChanges)

    dirtyCheckingRepository.clearAllDirtyVegobjektIds(featureType.typeId)
    dirtyCheckingRepository.clearAllDirtyVeglenkesekvenser()

    vegobjekterRepository.cleanOldVersions()
}

private fun Services.getTimestampFromLastExport(): Instant = (
    s3TimestampService.getLastUpdateTimestamp(ExportedFeatureType.SpeedLimit)
        ?: s3TimestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit)
        ?: error("Ingen tidligere snapshot eller oppdateringstidspunkt funnet for fartsgrenser")
    )

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
            val changedMainVegobjekterCounts =
                mainVegobjektTyper.map { typeId ->
                    async {
                        vegobjekterService.updateVegobjekter(typeId, true)
                    }
                }
            val changedSupportingVegobjekterCounts =
                supportingVegobjektTyper.map { typeId ->
                    async {
                        vegobjekterService.updateVegobjekter(typeId, false)
                    }
                }

            val total =
                veglenkesekvensHendelseCount.await() + changedMainVegobjekterCounts.sumOf { it.await() } +
                    changedSupportingVegobjekterCounts.sumOf { it.await() }
        } while (total > 0)
    }
    return now
}

private fun Services.performRestoreIfNeeded() {
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
