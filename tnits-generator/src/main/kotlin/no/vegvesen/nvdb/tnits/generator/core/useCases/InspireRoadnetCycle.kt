package no.vegvesen.nvdb.tnits.generator.core.useCases

import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.generator.core.api.BackfillOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.api.InspireRoadNetExporter
import no.vegvesen.nvdb.tnits.generator.core.api.LocalBackupService
import no.vegvesen.nvdb.tnits.generator.core.api.UpdateOrchestrator
import no.vegvesen.nvdb.tnits.generator.core.services.HealthCheckService
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.CachedVegnett
import kotlin.time.Clock

@Singleton
class InspireRoadnetCycle(
    private val rocksDbBackupService: LocalBackupService,
    private val backfillOrchestrator: BackfillOrchestrator,
    private val updateOrchestrator: UpdateOrchestrator,
    private val cachedVegnett: CachedVegnett,
    private val inspireRoadNetExporter: InspireRoadNetExporter,
    private val healthCheckService: HealthCheckService,
    private val clock: Clock,
) {

    suspend fun execute() {
        healthCheckService.verifyConnections()
        rocksDbBackupService.restoreIfNeeded()
        val backfillCount = backfillOrchestrator.performBackfill()
        val updateCount = updateOrchestrator.performUpdate()
        val timestamp = clock.now()
        if (backfillCount + updateCount > 0) {
            // First backup before cache, in case of OOM (remove when OOM is not a concern anymore)
            rocksDbBackupService.createBackup()
        }
        cachedVegnett.initialize()
        inspireRoadNetExporter.exportRoadNet(timestamp)
        rocksDbBackupService.createBackup()
    }
}
