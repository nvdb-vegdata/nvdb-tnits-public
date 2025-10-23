package no.vegvesen.nvdb.tnits.generator.core.api

import kotlin.time.Instant

interface InspireRoadNetExporter {
    suspend fun exportRoadNet(timestamp: Instant)
}
