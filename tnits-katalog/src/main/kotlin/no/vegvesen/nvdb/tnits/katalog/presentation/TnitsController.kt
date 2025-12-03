package no.vegvesen.nvdb.tnits.katalog.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.vegvesen.nvdb.tnits.common.extensions.OsloZoneId
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.katalog.config.AppConfiguration
import no.vegvesen.nvdb.tnits.katalog.core.api.FileService
import no.vegvesen.nvdb.tnits.katalog.presentation.model.*
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val snapshotSuffix = "/snapshot.xml.gz"
private const val updateSuffix = "/update.xml.gz"

private val OsloClock = Clock.system(OsloZoneId)

@RestController
@RequestMapping("/api/v1/tnits")
@Tag(name = "TN-ITS")
class TnitsController(
    private val appConfiguration: AppConfiguration,
    private val fileService: FileService,
) {

    @Operation(description = """List available road feature types for TN-ITS data export.""")
    @GetMapping("/types")
    fun getExportedFeatureTypeCodes(): List<ExportedFeatureType> = ExportedFeatureType.entries

    @Operation(description = "List available snapshots for a given road feature type.")
    @GetMapping("/{type}/snapshots")
    fun getSnapshots(
        @PathVariable @Parameter(description = "Road feature type to list snapshots for, see /api/v1/tnits/types") type: ExportedFeatureType,
    ): SnapshotsResponse = fileService.getFileObjects(type, snapshotSuffix)
        .sortedByDescending { it.timestamp }
        .map { (objectName, timestamp, size) ->
            Snapshot(
                href = getDownloadUrl(objectName),
                timestamp = timestamp,
                size = size,
            )
        }
        .ifEmpty { throw ResponseStatusException(HttpStatus.NOT_FOUND) }
        .let { SnapshotsResponse(it) }

    @Operation(description = "Get the most recent full snapshot for a given road feature type.")
    @GetMapping("/{type}/snapshots/latest")
    fun getLatestSnapshot(@PathVariable @Parameter(description = "Road feature type, see /api/v1/tnits/types") type: ExportedFeatureType): SnapshotResponse =
        fileService.getFileObjects(type, snapshotSuffix)
            .maxByOrNull { it.timestamp }
            ?.let { (objectName, timestamp) ->
                SnapshotResponse(
                    href = getDownloadUrl(objectName),
                    newUpdates = getUpdatesUrl(type, timestamp),
                )
            } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun getUpdatesUrl(type: ExportedFeatureType, timestamp: Instant): String = "${appConfiguration.baseUrl}/api/v1/tnits/$type/updates?from=$timestamp"

    private fun getDownloadUrl(objectName: String): String = "${appConfiguration.baseUrl}/api/v1/download?path=$objectName"

    @Operation(description = "Get delta updates for a given road feature type after a specified timestamp.")
    @GetMapping("/{type}/updates")
    fun getUpdatesFrom(
        @PathVariable @Parameter(description = "Road feature type, see /api/v1/tnits/types") type: ExportedFeatureType,
        @RequestParam @Parameter(
            description = """
Timestamp to get updates after. ISO-format, can be date (`2025-01-01`) or UTC timestamp (`2025-01-05T12:00:00Z`).
If not specified, fetches updates since start of current month.
        """,
        ) from: Instant = Instant.now(
            OsloClock,
        )
            .atZone(OsloZoneId)
            .withDayOfMonth(1)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant(),
    ): UpdatesResponse = fileService.getFileObjects(type, updateSuffix)
        .filter { it.timestamp > from }
        .sortedByDescending { it.timestamp }
        .let { updates ->
            UpdatesResponse(
                updates = updates.map { (objectName, timestamp, size) ->
                    Update(
                        href = getDownloadUrl(objectName),
                        timestamp = timestamp,
                        size = size,
                    )
                },
                newUpdates = getUpdatesUrl(type, updates.lastOrNull()?.timestamp ?: from),
            )
        }
}
