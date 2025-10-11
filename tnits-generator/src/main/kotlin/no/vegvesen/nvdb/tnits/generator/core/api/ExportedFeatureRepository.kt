package no.vegvesen.nvdb.tnits.generator.core.api

import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsFeature

interface ExportedFeatureRepository {
    fun batchUpdate(featuresById: Map<Long, TnitsFeature>)
    fun getExportedFeatures(vegobjektIds: Collection<Long>): Map<Long, TnitsFeature>
}
