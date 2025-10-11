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
class PerformTnitsSnapshotExport(
    private val rocksDbBackupService: RocksDbS3BackupService,
    private val nvdbBackfillOrchestrator: NvdbBackfillOrchestrator,
    private val nvdbUpdateOrchestrator: NvdbUpdateOrchestrator,
    private val cachedVegnett: CachedVegnett,
    private val tnitsExportService: TnitsExportService,
) {
    suspend fun execute() {
        rocksDbBackupService.restoreIfNeeded()
        nvdbBackfillOrchestrator.performBackfill()
        nvdbUpdateOrchestrator.performUpdate()
        val timestamp = Clock.System.now()
        // First backup before cache, in case of OOM
        rocksDbBackupService.createBackup()
        cachedVegnett.initialize()
        tnitsExportService.exportSnapshot(timestamp, ExportedFeatureType.SpeedLimit)
        // Second backup after cache and export (updated hashes etc.)
        rocksDbBackupService.createBackup()
    }
}
