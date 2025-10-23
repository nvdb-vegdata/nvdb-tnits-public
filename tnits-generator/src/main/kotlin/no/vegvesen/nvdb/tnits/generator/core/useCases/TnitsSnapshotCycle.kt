package no.vegvesen.nvdb.tnits.generator.core.useCases

import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.api.BackfillOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.api.LocalBackupService
import no.vegvesen.nvdb.tnits.generator.core.api.UpdateOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.services.tnits.TnitsExportService
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.CachedVegnett
import kotlin.time.Clock

@Singleton
class TnitsSnapshotCycle(
    private val localBackupService: LocalBackupService,
    private val backfillOrchestrator: BackfillOrchestrator,
    private val updateOrchestrator: UpdateOrchestrator,
    private val cachedVegnett: CachedVegnett,
    private val tnitsExportService: TnitsExportService,
    private val clock: Clock,
) {
    suspend fun execute() {
        localBackupService.restoreIfNeeded()
        val backfillCount = backfillOrchestrator.performBackfill()
        val updateCount = updateOrchestrator.performUpdate()
        val timestamp = clock.now()
        if (backfillCount + updateCount > 0) {
            // First backup before cache, in case of OOM (remove when OOM is not a concern anymore)
            localBackupService.createBackup()
        }
        cachedVegnett.initialize()
        for (featureType in ExportedFeatureType.entries) {
            tnitsExportService.exportSnapshot(timestamp, featureType)
        }
        // Second backup after cache and export (updated hashes etc.)
        localBackupService.createBackup()
    }
}
