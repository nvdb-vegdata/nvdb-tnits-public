package no.vegvesen.nvdb.tnits

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import kotlinx.datetime.toLocalDateTime
import no.vegvesen.nvdb.tnits.Services.Companion.withServices
import no.vegvesen.nvdb.tnits.extensions.OsloZone
import no.vegvesen.nvdb.tnits.extensions.getLastUpdateCheck
import no.vegvesen.nvdb.tnits.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Clock

val mainVegobjektTyper = listOf(VegobjektTyper.FARTSGRENSE)

val supportingVegobjektTyper = setOf(VegobjektTyper.FELTSTREKNING, VegobjektTyper.FUNKSJONELL_VEGKLASSE)

val log: Logger = LoggerFactory.getLogger("tnits")

suspend fun main(args: Array<String>) {
    log.info("Starting NVDB TN-ITS application on process ${ProcessHandle.current().pid()}")
    NvdbTnitsApp
        .subcommands(SnapshotCommand, UpdateCommand, AutoCommand)
        .main(args)
    log.info("NVDB TN-ITS application finished")
}

object NvdbTnitsApp : SuspendingCliktCommand() {
    override val invokeWithoutSubcommand = true

    override suspend fun run() {
        val subcommand = currentContext.invokedSubcommand
        if (subcommand == null) {
            // No subcommand specified, run AutoCommand by default
            AutoCommand.run()
        }
    }
}

abstract class BaseCommand : SuspendingCliktCommand()

object SnapshotCommand : BaseCommand() {
    override suspend fun run() {
        withServices {
            rocksDbBackupService.restoreIfNeeded()
            performBackfillHandler.performBackfill()
            performUpdateHandler.performUpdate()
            val timestamp = Clock.System.now()
            // First backup before cache, in case of OOM
            rocksDbBackupService.createBackup()
            cachedVegnett.initialize()
            tnitsFeatureExporter.exportSnapshot(timestamp, ExportedFeatureType.SpeedLimit)
            // Second backup after cache and export (updated hashes etc.)
            rocksDbBackupService.createBackup()
        }
    }
}

object UpdateCommand : BaseCommand() {
    override suspend fun run() {
        withServices {
            rocksDbBackupService.restoreIfNeeded()
            performBackfillHandler.performBackfill()
            performUpdateHandler.performUpdate()
            val timestamp = Clock.System.now()
            // First backup before cache, in case of OOM
            rocksDbBackupService.createBackup()
            cachedVegnett.initialize()
            exportUpdateHandler.exportUpdate(timestamp, ExportedFeatureType.SpeedLimit)
            // Second backup after cache and export (updated hashes etc.)
            rocksDbBackupService.createBackup()
        }
    }
}

object AutoCommand : BaseCommand() {
    override suspend fun run() {
        withServices {
            rocksDbBackupService.restoreIfNeeded()
            val lastSnapshot = s3TimestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit)
            val lastUpdate = s3TimestampService.getLastUpdateTimestamp(ExportedFeatureType.SpeedLimit)

            // TODO: Make configurable somehow
            val hasSnapshotBeenTakenThisMonth = lastSnapshot?.let {
                val now = Clock.System.now().toLocalDateTime(OsloZone).date
                val snapshotDate = it.toLocalDateTime(OsloZone).date
                now.year == snapshotDate.year && now.month == snapshotDate.month
            } ?: false

            val hasUpdateBeenTakenToday = lastUpdate?.let {
                val now = Clock.System.now().toLocalDateTime(OsloZone).date
                val updateDate = it.toLocalDateTime(OsloZone).date
                now == updateDate
            } ?: false

            val hasUpdateBeenCheckedToday = keyValueStore.getLastUpdateCheck(ExportedFeatureType.SpeedLimit)?.let {
                val now = Clock.System.now().toLocalDateTime(OsloZone).date
                val updateCheckDate = it.toLocalDateTime(OsloZone).date
                now == updateCheckDate
            } ?: false

            val shouldPerformSnapshot = !hasSnapshotBeenTakenThisMonth
            val shouldPerformUpdate = !hasUpdateBeenTakenToday && !hasUpdateBeenCheckedToday

            if (shouldPerformSnapshot || shouldPerformUpdate) {
                log.info("Starting automatic TN-ITS process. shouldPerformSnapshot=$shouldPerformSnapshot, shouldPerformUpdate=$shouldPerformUpdate")
                performBackfillHandler.performBackfill()
                performUpdateHandler.performUpdate()
                val timestamp = Clock.System.now()
                // First backup before cache, in case of OOM
                rocksDbBackupService.createBackup()
                cachedVegnett.initialize()
                if (shouldPerformSnapshot) {
                    tnitsFeatureExporter.exportSnapshot(timestamp, ExportedFeatureType.SpeedLimit)
                }
                if (shouldPerformUpdate) {
                    exportUpdateHandler.exportUpdate(timestamp, ExportedFeatureType.SpeedLimit)
                }
                // Second backup after cache and export (updated hashes etc.)
                rocksDbBackupService.createBackup()
                log.info("Automatic TN-ITS process finished")
            } else {
                log.info(
                    "Skipping automatic TN-ITS process, already performed today or this month. hasSnapshotBeenTakenThisMonth=${true}, hasUpdateBeenTakenToday=$hasUpdateBeenTakenToday, hasUpdateBeenCheckedToday=$hasUpdateBeenCheckedToday",
                )
            }
        }
    }
}
