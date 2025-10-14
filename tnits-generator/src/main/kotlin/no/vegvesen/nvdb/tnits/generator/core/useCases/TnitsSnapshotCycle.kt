package no.vegvesen.nvdb.tnits.generator.core.useCases

import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.services.nvdb.NvdbBackfillOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.services.nvdb.NvdbUpdateOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.services.tnits.TnitsExportService
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.generator.infrastructure.RocksDbS3BackupService
import kotlin.time.Clock

@Singleton
class TnitsSnapshotCycle(
    private val rocksDbBackupService: RocksDbS3BackupService,
    private val nvdbBackfillOrchestrator: NvdbBackfillOrchestrator,
    private val nvdbUpdateOrchestrator: NvdbUpdateOrchestrator,
    private val cachedVegnett: CachedVegnett,
    private val tnitsExportService: TnitsExportService,
) {
    suspend fun execute() {
        rocksDbBackupService.restoreIfNeeded()
        val backfillCount = nvdbBackfillOrchestrator.performBackfill()
        val updateCount = nvdbUpdateOrchestrator.performUpdate()
        val timestamp = Clock.System.now()
        if (backfillCount + updateCount > 0) {
            // First backup before cache, in case of OOM (remove when OOM is not a concern anymore)
            rocksDbBackupService.createBackup()
        }
        cachedVegnett.initialize()
        for (featureType in ExportedFeatureType.entries) {
            tnitsExportService.exportSnapshot(timestamp, featureType)
        }
        // Second backup after cache and export (updated hashes etc.)
        rocksDbBackupService.createBackup()
    }
}
