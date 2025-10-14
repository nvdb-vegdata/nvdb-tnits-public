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

    data class UpdateStatus(
        val pendingSnapshot: Boolean,
        val pendingUpdate: Boolean,
    )

    suspend fun execute() {
        rocksDbBackupService.restoreIfNeeded()

        val updateStatusByType = ExportedFeatureType.entries.associateWith { exportedFeatureType ->
            val lastSnapshot = timestampService.getLastSnapshotTimestamp(exportedFeatureType)
            val lastUpdate = timestampService.getLastUpdateTimestamp(exportedFeatureType)

            val hasSnapshotBeenTakenThisMonth = lastSnapshot?.let {
                val snapshotDate = it.toLocalDateTime(OsloZone).date
                today.year == snapshotDate.year && today.month == snapshotDate.month
            } ?: false

            val hasUpdateBeenTakenToday = lastUpdate?.let {
                val updateDate = it.toLocalDateTime(OsloZone).date
                today == updateDate
            } ?: false

            val hasUpdateBeenCheckedToday = keyValueStore.getLastUpdateCheck(exportedFeatureType)?.let {
                val updateCheckDate = it.toLocalDateTime(OsloZone).date
                today == updateCheckDate
            } ?: false

            val shouldPerformSnapshot = !hasSnapshotBeenTakenThisMonth
            val shouldPerformUpdate = !hasUpdateBeenTakenToday && !hasUpdateBeenCheckedToday
            UpdateStatus(shouldPerformSnapshot, shouldPerformUpdate)
        }

        if (updateStatusByType.values.any { it.pendingSnapshot || it.pendingUpdate }) {
            log.info("Starting automatic TN-ITS process. Update status by type: $updateStatusByType")
            val backfillCount = nvdbBackfillOrchestrator.performBackfill()
            val updateCount = nvdbUpdateOrchestrator.performUpdate()
            val timestamp = Clock.System.now()
            if (backfillCount + updateCount > 0) {
                // First backup before cache, in case of OOM (remove when OOM is not a concern anymore)
                rocksDbBackupService.createBackup()
            }
            cachedVegnett.initialize()
            // Run update before snapshot, because snapshot will update hashes (thus causing us to miss updates if we did snapshot first)
            for ((exportedFeatureType, updateStatus) in updateStatusByType) {
                if (updateStatus.pendingUpdate) {
                    tnitsExportService.exportUpdate(timestamp, exportedFeatureType)
                }
                if (updateStatus.pendingSnapshot) {
                    tnitsExportService.exportSnapshot(timestamp, exportedFeatureType)
                }
            }

            // Second backup after cache and export (updated hashes etc.)
            rocksDbBackupService.createBackup()
            log.info("Automatic TN-ITS process finished")
        } else {
            log.info(
                "Skipping automatic TN-ITS process, already performed today or this month",
            )
        }
    }
}
