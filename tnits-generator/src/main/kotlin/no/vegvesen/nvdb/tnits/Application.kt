package no.vegvesen.nvdb.tnits

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import no.vegvesen.nvdb.tnits.Services.Companion.withServices
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
    override suspend fun run() {
        // This method is called for subcommands, but we don't want to print anything here
        // The help is handled automatically by Clikt when no subcommand is provided
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
            cachedVegnett.initialize()
            tnitsFeatureExporter.exportSnapshot(timestamp, ExportedFeatureType.SpeedLimit)
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
            cachedVegnett.initialize()
            exportUpdateHandler.exportUpdate(timestamp, ExportedFeatureType.SpeedLimit)
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
