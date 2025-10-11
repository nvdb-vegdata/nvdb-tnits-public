package no.vegvesen.nvdb.tnits.generator.core.useCases

import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.generator.core.api.LocalBackupService
import no.vegvesen.nvdb.tnits.generator.core.services.HealthCheckService
import no.vegvesen.nvdb.tnits.generator.core.services.nvdb.NvdbBackfillOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.services.nvdb.NvdbUpdateOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.generator.infrastructure.s3.InspireRoadNetExporter
import kotlin.time.Clock

@Singleton
class InspireRoadnetCycle(
    private val rocksDbBackupService: LocalBackupService,
    private val nvdbBackfillOrchestrator: NvdbBackfillOrchestrator,
    private val nvdbUpdateOrchestrator: NvdbUpdateOrchestrator,
    private val cachedVegnett: CachedVegnett,
    private val inspireRoadNetExporter: InspireRoadNetExporter,
    private val healthCheckService: HealthCheckService,
) {

    suspend fun execute() {
        healthCheckService.verifyConnections()
        rocksDbBackupService.restoreIfNeeded()
        nvdbBackfillOrchestrator.performBackfill()
        nvdbUpdateOrchestrator.performUpdate()
        val timestamp = Clock.System.now()
        rocksDbBackupService.createBackup()
        cachedVegnett.initialize()
        inspireRoadNetExporter.exportRoadNet(timestamp)
        rocksDbBackupService.createBackup()
    }
}
