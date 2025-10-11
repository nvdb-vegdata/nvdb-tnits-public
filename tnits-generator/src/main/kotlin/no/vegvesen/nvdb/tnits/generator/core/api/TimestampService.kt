package no.vegvesen.nvdb.tnits.generator.core.api

import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import kotlin.time.Instant

interface TimestampService {
    fun getLastSnapshotTimestamp(featureType: ExportedFeatureType): Instant?
    fun getLastUpdateTimestamp(featureType: ExportedFeatureType): Instant?
}
