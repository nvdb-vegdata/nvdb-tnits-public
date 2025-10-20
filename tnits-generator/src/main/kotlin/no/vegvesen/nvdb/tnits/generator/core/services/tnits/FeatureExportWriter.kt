package no.vegvesen.nvdb.tnits.generator.core.services.tnits

import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.api.ExportedFeatureRepository
import no.vegvesen.nvdb.tnits.generator.core.api.TnitsFeatureExporter
import no.vegvesen.nvdb.tnits.generator.core.extensions.splitBuffered
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsExportType
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsFeature
import no.vegvesen.nvdb.tnits.generator.core.presentation.TnitsXmlWriter
import kotlin.time.Instant

@Singleton
class FeatureExportWriter(
    private val featureExporter: TnitsFeatureExporter,
    private val exportedFeatureRepository: ExportedFeatureRepository,
) : WithLogger {

    // Export features by simultaneously writing to S3 and the local database (for comparing later updates)
    // Saves a bit of time, but perhaps over-engineering?
    suspend fun exportFeatures(
        timestamp: Instant,
        featureType: ExportedFeatureType,
        featureFlow: Flow<TnitsFeature>,
        exportType: TnitsExportType,
        lastTimestamp: Instant? = null,
    ) {
        coroutineScope {
            val (first, second) = featureFlow.splitBuffered(bufferSize = 10000)

            launch(Dispatchers.IO) {
                var count = 0
                first.chunked(10000).collect { chunk ->
                    exportedFeatureRepository.batchUpdate(chunk.associateBy { it.id })
                    count += chunk.size
                    log.debug("Lagret {} TnitsFeature med type {}", count, featureType)
                }
            }

            launch(Dispatchers.IO) {
                try {
                    featureExporter.openExportStream(timestamp, exportType, featureType).use { outputStream ->
                        TnitsXmlWriter.writeFeaturesToXml(timestamp, outputStream, featureType, second, exportType, lastTimestamp)
                    }
                } catch (e: Exception) {
                    log.error("Eksport til S3 feilet", e)
                }
            }
        }
    }
}
