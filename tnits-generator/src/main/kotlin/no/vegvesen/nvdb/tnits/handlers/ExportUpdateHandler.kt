package no.vegvesen.nvdb.tnits.handlers

import no.vegvesen.nvdb.tnits.TnitsFeatureExporter
import no.vegvesen.nvdb.tnits.extensions.putLastUpdateCheck
import no.vegvesen.nvdb.tnits.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.storage.DirtyCheckingRepository
import no.vegvesen.nvdb.tnits.storage.KeyValueRocksDbStore
import no.vegvesen.nvdb.tnits.storage.VegobjekterRepository
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import kotlin.time.Instant

class ExportUpdateHandler(
    private val tnitsFeatureExporter: TnitsFeatureExporter,
    private val dirtyCheckingRepository: DirtyCheckingRepository,
    private val vegobjekterRepository: VegobjekterRepository,
    private val keyValueStore: KeyValueRocksDbStore,
) : WithLogger {
    suspend fun exportUpdate(timestamp: Instant, featureType: ExportedFeatureType) {
        log.info("Genererer delta snapshot av TN-ITS ${featureType.typeCode}...")
        val vegobjektChanges = dirtyCheckingRepository.getDirtyVegobjektChanges(featureType.typeId)

        if (vegobjektChanges.isEmpty()) {
            log.info("Ingen endringer siden forrige eksport, hopper over eksport")
        } else {
            log.info("Eksporterer ${vegobjektChanges.size} endrede vegobjekter for TN-ITS ${featureType.typeCode}...")

            // TODO: Handle if we lose dirty checking data (e.g., after a crash), by checking timestamp for latest export, and fetching hendelser

            tnitsFeatureExporter.exportUpdate(timestamp, featureType, vegobjektChanges)

            dirtyCheckingRepository.clearAllDirtyVegobjektIds(featureType.typeId)
            dirtyCheckingRepository.clearAllDirtyVeglenkesekvenser()

            vegobjekterRepository.cleanOldVersions()
        }

        keyValueStore.putLastUpdateCheck(featureType, timestamp)
    }
}
