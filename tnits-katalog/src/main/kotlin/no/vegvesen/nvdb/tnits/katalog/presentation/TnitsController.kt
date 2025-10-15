package no.vegvesen.nvdb.tnits.katalog.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.vegvesen.nvdb.tnits.common.model.RoadFeatureTypeCode
import no.vegvesen.nvdb.tnits.katalog.config.AppConfiguration
import no.vegvesen.nvdb.tnits.katalog.core.api.FileService
import no.vegvesen.nvdb.tnits.katalog.presentation.model.Snapshot
import no.vegvesen.nvdb.tnits.katalog.presentation.model.SnapshotResponse
import no.vegvesen.nvdb.tnits.katalog.presentation.model.SnapshotsResponse
import no.vegvesen.nvdb.tnits.katalog.presentation.model.UpdatesResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

private const val snapshotSuffix = "/snapshot.xml.gz"
private const val updateSuffix = "/update.xml.gz"

@RestController
@RequestMapping("/api/v1/tnits")
@Tag(name = "TN-ITS")
class TnitsController(
    private val appConfiguration: AppConfiguration,
    private val fileService: FileService,
) {

    @Operation(
        description = """List available road feature types for TN-ITS data export,
            |based on [TN-ITS codelists](http://spec.tn-its.eu/codelists/RoadFeatureTypeCode.xml).""",
    )
    @GetMapping("/types")
    fun getExportedFeatureTypeCodes(): List<RoadFeatureTypeCode> = RoadFeatureTypeCode.entries

    @Operation(description = "List available snapshots for a given road feature type.")
    @GetMapping("/{type}/snapshots")
    fun getSnapshots(
        @PathVariable @Parameter(description = "TN-ITS RoadFeatureTypeCode to list snapshots for, see /api/v1/tnits/types") type: RoadFeatureTypeCode,
    ): SnapshotsResponse = fileService.getFileObjects(type, snapshotSuffix)
        .sortedByDescending { it.timestamp }
        .map { (objectName, timestamp) ->
            Snapshot(
                href = getDownloadUrl(objectName),
                timestamp = timestamp,
            )
        }
        .ifEmpty { throw ResponseStatusException(HttpStatus.NOT_FOUND) }
        .let { SnapshotsResponse(it) }

    @Operation(description = "Get the most recent full snapshot for a given road feature type.")
    @GetMapping("/{type}/snapshots/latest")
    fun getLatestSnapshot(
        @PathVariable @Parameter(description = "TN-ITS RoadFeatureTypeCode, see /api/v1/tnits/types") type: RoadFeatureTypeCode,
    ): SnapshotResponse = fileService.getFileObjects(type, snapshotSuffix)
        .maxByOrNull { it.timestamp }
        ?.let { (objectName, timestamp) ->
            SnapshotResponse(
                href = getDownloadUrl(objectName),
                newUpdates = getUpdatesUrl(type, timestamp),
            )
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun getUpdatesUrl(type: RoadFeatureTypeCode, timestamp: Instant): String = "${appConfiguration.baseUrl}/api/v1/tnits/$type/updates?from=$timestamp"

    private fun getDownloadUrl(objectName: String): String = "${appConfiguration.baseUrl}/api/v1/download?path=$objectName"

    @Operation(description = "Get delta updates for a given road feature type after a specified timestamp.")
    @GetMapping("/{type}/updates")
    fun getUpdatesFrom(
        @PathVariable @Parameter(description = "TN-ITS RoadFeatureTypeCode, see /api/v1/tnits/types") type: RoadFeatureTypeCode,
        @RequestParam @Parameter(description = "Timestamp to get updates after") from: Instant,
    ): UpdatesResponse = fileService.getFileObjects(type, updateSuffix)
        .filter { it.timestamp > from }
        .sortedBy { it.timestamp }
        .let { updates ->
            UpdatesResponse(
                hrefs = updates.map { getDownloadUrl(it.objectName) },
                newUpdates = getUpdatesUrl(type, updates.lastOrNull()?.timestamp ?: from),
            )
        }
}
