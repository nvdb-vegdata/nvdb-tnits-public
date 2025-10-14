package no.vegvesen.nvdb.tnits.katalog.presentation

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
import kotlin.time.Instant

private const val snapshotSuffix = "/snapshot.xml.gz"
private const val updateSuffix = "/update.xml.gz"

@RestController
@RequestMapping("/api/v1/tnits")
@Tag(name = "TN-ITS")
class TnitsController(
    private val appConfiguration: AppConfiguration,
    private val fileService: FileService,
) {

    @GetMapping("/types")
    fun getExportedFeatureTypeCodes(): List<RoadFeatureTypeCode> = RoadFeatureTypeCode.entries

    @GetMapping("/{type}/snapshots")
    fun getSnapshots(@PathVariable type: RoadFeatureTypeCode): SnapshotsResponse = fileService.getFileObjects(type, snapshotSuffix)
        .sortedByDescending { it.timestamp }
        .map { (objectName, timestamp) ->
            Snapshot(
                href = getDownloadUrl(objectName),
                timestamp = timestamp,
            )
        }
        .ifEmpty { throw ResponseStatusException(HttpStatus.NOT_FOUND) }
        .let { SnapshotsResponse(it) }

    @GetMapping("/{type}/snapshots/latest")
    fun getLatestSnapshot(@PathVariable type: RoadFeatureTypeCode): SnapshotResponse = fileService.getFileObjects(type, snapshotSuffix)
        .maxByOrNull { it.timestamp }
        ?.let { (objectName, timestamp) ->
            SnapshotResponse(
                href = getDownloadUrl(objectName),
                newUpdates = getUpdatesUrl(timestamp),
            )
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun getUpdatesUrl(timestamp: Instant): String = "${appConfiguration.baseUrl}/api/v1/tnits/updates?from=$timestamp"

    private fun getDownloadUrl(objectName: String): String = "${appConfiguration.baseUrl}/api/v1/download?path=$objectName"

    @GetMapping("/{type}/updates")
    fun getUpdatesFrom(@PathVariable type: RoadFeatureTypeCode, @RequestParam from: Instant): UpdatesResponse = fileService.getFileObjects(type, updateSuffix)
        .filter { it.timestamp > from }
        .sortedBy { it.timestamp }
        .let { updates ->
            UpdatesResponse(
                hrefs = updates.map { getDownloadUrl(it.objectName) },
                newUpdates = getUpdatesUrl(updates.lastOrNull()?.timestamp ?: from),
            )
        }
}
