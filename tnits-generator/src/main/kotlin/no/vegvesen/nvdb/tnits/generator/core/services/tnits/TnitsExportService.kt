package no.vegvesen.nvdb.tnits.generator.core.services.tnits

import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.api.DirtyCheckingRepository
import no.vegvesen.nvdb.tnits.generator.core.api.KeyValueStore
import no.vegvesen.nvdb.tnits.generator.core.api.VegobjekterRepository
import no.vegvesen.nvdb.tnits.generator.core.extensions.putLastUpdateCheck
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsExportType
import kotlin.time.Instant

@Singleton
class TnitsExportService(
    private val featureTransformer: FeatureTransformer,
    private val exportWriter: FeatureExportWriter,
    private val dirtyCheckingRepository: DirtyCheckingRepository,
    private val vegobjekterRepository: VegobjekterRepository,
    private val keyValueStore: KeyValueStore,
) : WithLogger {

    suspend fun exportUpdate(timestamp: Instant, featureType: ExportedFeatureType, lastUpdate: Instant? = null) {
        log.info("Genererer delta snapshot av TN-ITS ${featureType.typeCode}...")
        val changesById = dirtyCheckingRepository.getDirtyVegobjektChangesAsMap(featureType.typeId)

        if (changesById.isEmpty()) {
            log.info("Ingen endringer siden forrige eksport, hopper over eksport")
        } else {
            log.info("Eksporterer ${changesById.size} endrede vegobjekter for TN-ITS ${featureType.typeCode}...")

            // TODO: Handle if we lose dirty checking data (e.g., after a crash), by checking timestamp for latest export, and fetching hendelser

            // Note: The following sequence is not transactional / atomic. The operations have been set up such that worst case, we export some changes twice.

            val featureFlow = featureTransformer.generateFeaturesUpdate(featureType, changesById, timestamp)
            exportWriter.exportFeatures(timestamp, featureType, featureFlow, TnitsExportType.Update, lastUpdate)

            dirtyCheckingRepository.clearAllDirtyVegobjektIds(featureType.typeId)
            dirtyCheckingRepository.clearAllDirtyVeglenkesekvenser()

            vegobjekterRepository.cleanOldVersions()
        }

        keyValueStore.putLastUpdateCheck(featureType, timestamp)
    }

    suspend fun exportSnapshot(timestamp: Instant, featureType: ExportedFeatureType) {
        log.info("Genererer fullt snapshot av TN-ITS ${featureType.typeCode}...")
        val featureFlow = featureTransformer.generateSnapshot(featureType)
        exportWriter.exportFeatures(timestamp, featureType, featureFlow, TnitsExportType.Snapshot)
    }
}
