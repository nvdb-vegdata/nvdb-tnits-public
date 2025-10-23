package no.vegvesen.nvdb.tnits.generator.core.useCases

import jakarta.inject.Singleton
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.api.BackfillOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.api.KeyValueStore
import no.vegvesen.nvdb.tnits.generator.core.api.LocalBackupService
import no.vegvesen.nvdb.tnits.generator.core.api.TimestampService
import no.vegvesen.nvdb.tnits.generator.core.api.UpdateOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.extensions.OsloZone
import no.vegvesen.nvdb.tnits.generator.core.extensions.getLastUpdateCheck
import no.vegvesen.nvdb.tnits.generator.core.services.tnits.TnitsExportService
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.CachedVegnett
import kotlin.time.Clock
import kotlin.time.Instant

@Singleton
class TnitsAutomaticCycle(
    private val rocksDbBackupService: LocalBackupService,
    private val timestampService: TimestampService,
    private val keyValueStore: KeyValueStore,
    private val backfillOrchestrator: BackfillOrchestrator,
    private val updateOrchestrator: UpdateOrchestrator,
    private val cachedVegnett: CachedVegnett,
    private val tnitsExportService: TnitsExportService,
    private val clock: Clock,
) : WithLogger {

    data class UpdateStatus(
        val pendingSnapshot: Boolean,
        val pendingUpdate: Boolean,
        val lastUpdate: Instant?,
    )

    suspend fun execute() {
        rocksDbBackupService.restoreIfNeeded()

        val today = clock.todayIn(OsloZone)

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
            UpdateStatus(shouldPerformSnapshot, shouldPerformUpdate, lastUpdate)
        }

        if (updateStatusByType.values.any { it.pendingSnapshot || it.pendingUpdate }) {
            log.info("Starting automatic TN-ITS process. Update status by type: $updateStatusByType")
            val backfillCount = backfillOrchestrator.performBackfill()
            val updateCount = updateOrchestrator.performUpdate()
            val timestamp = clock.now()
            if (backfillCount + updateCount > 0) {
                // First backup before cache, in case of OOM (remove when OOM is not a concern anymore)
                rocksDbBackupService.createBackup()
            }
            cachedVegnett.initialize()
            // Run update before snapshot, because snapshot will update hashes (thus causing us to miss updates if we did snapshot first)
            for ((exportedFeatureType, updateStatus) in updateStatusByType) {
                if (updateStatus.pendingUpdate) {
                    tnitsExportService.exportUpdate(timestamp, exportedFeatureType, updateStatus.lastUpdate)
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
