package no.vegvesen.nvdb.tnits.generator.core.services.tnits

import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.config.RetentionConfig
import no.vegvesen.nvdb.tnits.generator.core.api.DirtyCheckingRepository
import no.vegvesen.nvdb.tnits.generator.core.api.KeyValueStore
import no.vegvesen.nvdb.tnits.generator.core.api.TimestampService
import no.vegvesen.nvdb.tnits.generator.core.api.VegobjekterRepository
import no.vegvesen.nvdb.tnits.generator.core.extensions.putLastUpdateCheck
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsExportType
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@Singleton
class TnitsExportService(
    private val featureTransformer: FeatureTransformer,
    private val exportWriter: FeatureExportWriter,
    private val dirtyCheckingRepository: DirtyCheckingRepository,
    private val vegobjekterRepository: VegobjekterRepository,
    private val keyValueStore: KeyValueStore,
    private val timestampService: TimestampService,
    private val clock: Clock,
    private val retentionConfig: RetentionConfig,
) : WithLogger {

    suspend fun exportUpdate(timestamp: Instant, featureType: ExportedFeatureType, lastUpdate: Instant? = null) {
        log.info("Genererer delta snapshot av TN-ITS $featureType...")
        val changesById = dirtyCheckingRepository.getDirtyVegobjektChanges(featureType.typeId)

        if (changesById.isEmpty()) {
            log.info("Ingen endringer siden forrige eksport, hopper over eksport")
        } else {
            log.info("Eksporterer ${changesById.size} endrede vegobjekter for TN-ITS $featureType...")

            // TODO: Handle if we lose dirty checking data (e.g., after a crash), by checking timestamp for latest export, and fetching hendelser

            // Note: The following sequence is not transactional / atomic. The operations have been set up such that worst case, we export some changes twice.

            val featureFlow = featureTransformer.generateFeaturesUpdate(featureType, changesById, timestamp)
            exportWriter.exportFeatures(timestamp, featureType, featureFlow, TnitsExportType.Update, lastUpdate)

            dirtyCheckingRepository.clearAllDirtyVegobjektIds(featureType.typeId)
            dirtyCheckingRepository.clearAllDirtyVeglenkesekvenser()

            vegobjekterRepository.cleanOldVersions(featureType.typeId)
        }

        keyValueStore.putLastUpdateCheck(featureType, timestamp)
    }

    suspend fun exportSnapshot(timestamp: Instant, featureType: ExportedFeatureType) {
        log.info("Genererer fullt snapshot av TN-ITS $featureType...")
        val featureFlow = featureTransformer.generateSnapshot(featureType)
        exportWriter.exportFeatures(timestamp, featureType, featureFlow, TnitsExportType.Snapshot)
    }

    fun deleteOldUpdates(featureType: ExportedFeatureType) {
        val timestamps = timestampService.findAllUpdateTimestamps(featureType)
        val cutoffDate = clock.now().minus(retentionConfig.deleteUpdatesAfterDays.days)
        val olderThanCutoff = timestamps.filter { it < cutoffDate }
        if (olderThanCutoff.isEmpty()) return
        for (timestamp in olderThanCutoff) {
            exportWriter.deleteExport(timestamp, TnitsExportType.Update, featureType)
        }
        log.info("Slettet ${olderThanCutoff.size} gamle TN-ITS $featureType oppdateringer")
    }

    fun deleteOldSnapshots(featureType: ExportedFeatureType) {
        val timestamps = timestampService.findAllSnapshotTimestamps(featureType)
        val twoMonthsAgo = clock.now().minus(retentionConfig.deleteSnapshotsAfterDays.days)
        val olderThanCutoff = timestamps.filter { it < twoMonthsAgo }
        if (olderThanCutoff.isEmpty()) return
        for (timestamp in olderThanCutoff) {
            exportWriter.deleteExport(timestamp, TnitsExportType.Snapshot, featureType)
        }
        log.info("Slettet ${olderThanCutoff.size} gamle TN-ITS $featureType snapshots")
    }
}
