package no.vegvesen.nvdb.tnits.generator.core.useCases

import jakarta.inject.Singleton
import kotlinx.datetime.toLocalDateTime
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.api.KeyValueStore
import no.vegvesen.nvdb.tnits.generator.core.api.LocalBackupService
import no.vegvesen.nvdb.tnits.generator.core.api.TimestampService
import no.vegvesen.nvdb.tnits.generator.core.extensions.OsloZone
import no.vegvesen.nvdb.tnits.generator.core.extensions.WithLogger
import no.vegvesen.nvdb.tnits.generator.core.extensions.getLastUpdateCheck
import no.vegvesen.nvdb.tnits.generator.core.extensions.today
import no.vegvesen.nvdb.tnits.generator.core.services.nvdb.NvdbBackfillOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.services.nvdb.NvdbUpdateOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.services.tnits.TnitsExportService
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.CachedVegnett
import kotlin.time.Clock

@Singleton
class TnitsAutomaticCycle(
    private val rocksDbBackupService: LocalBackupService,
    private val timestampService: TimestampService,
    private val keyValueStore: KeyValueStore,
    private val nvdbBackfillOrchestrator: NvdbBackfillOrchestrator,
    private val nvdbUpdateOrchestrator: NvdbUpdateOrchestrator,
    private val cachedVegnett: CachedVegnett,
    private val tnitsExportService: TnitsExportService,
) : WithLogger {

    suspend fun execute() {
        rocksDbBackupService.restoreIfNeeded()
        val lastSnapshot = timestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit)
        val lastUpdate = timestampService.getLastUpdateTimestamp(ExportedFeatureType.SpeedLimit)

        // TODO: Make configurable somehow?
        val hasSnapshotBeenTakenThisMonth = lastSnapshot?.let {
            val snapshotDate = it.toLocalDateTime(OsloZone).date
            today.year == snapshotDate.year && today.month == snapshotDate.month
        } ?: false

        val hasUpdateBeenTakenToday = lastUpdate?.let {
            val updateDate = it.toLocalDateTime(OsloZone).date
            today == updateDate
        } ?: false

        val hasUpdateBeenCheckedToday = keyValueStore.getLastUpdateCheck(ExportedFeatureType.SpeedLimit)?.let {
            val updateCheckDate = it.toLocalDateTime(OsloZone).date
            today == updateCheckDate
        } ?: false

        val shouldPerformSnapshot = !hasSnapshotBeenTakenThisMonth
        val shouldPerformUpdate = !hasUpdateBeenTakenToday && !hasUpdateBeenCheckedToday

        if (shouldPerformSnapshot || shouldPerformUpdate) {
            log.info("Starting automatic TN-ITS process. shouldPerformSnapshot=$shouldPerformSnapshot, shouldPerformUpdate=$shouldPerformUpdate")
            nvdbBackfillOrchestrator.performBackfill()
            nvdbUpdateOrchestrator.performUpdate()
            val timestamp = Clock.System.now()
            // First backup before cache, in case of OOM (remove when OOM is not a concern anymore)
            rocksDbBackupService.createBackup()
            cachedVegnett.initialize()
            // Run update before snapshot, because snapshot will update hashes (thus causing us to miss updates if we did snapshot first)
            if (shouldPerformUpdate) {
                tnitsExportService.exportUpdate(timestamp, ExportedFeatureType.SpeedLimit)
            }
            if (shouldPerformSnapshot) {
                tnitsExportService.exportSnapshot(timestamp, ExportedFeatureType.SpeedLimit)
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
