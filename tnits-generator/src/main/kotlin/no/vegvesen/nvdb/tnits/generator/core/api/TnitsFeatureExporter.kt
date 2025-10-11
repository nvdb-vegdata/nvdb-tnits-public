package no.vegvesen.nvdb.tnits.generator.core.api

import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsExportType
import java.io.OutputStream
import kotlin.time.Instant

interface TnitsFeatureExporter {
    fun openExportStream(timestamp: Instant, exportType: TnitsExportType, featureType: ExportedFeatureType): OutputStream
}
