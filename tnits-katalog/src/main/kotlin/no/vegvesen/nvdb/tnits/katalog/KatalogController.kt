package no.vegvesen.nvdb.tnits.katalog

import io.minio.ListObjectsArgs
import io.minio.MinioClient
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private const val speedLimitPrefix = "0105-speedLimit/"
private const val snapshotSuffix = "/snapshot.xml.gz"
private const val updateSuffix = "/update.xml.gz"

@RestController
@RequestMapping("/api/v1")
class KatalogController(private val minioClient: MinioClient, private val minioProperties: MinioProperties) {

    @GetMapping("/snapshots/latest")
    fun getLatestSnapshot(): SnapshotResponse = getSpeedLimitObjects(snapshotSuffix)
        .maxByOrNull { it.timestamp }
        ?.let { (objectName, timestamp) ->
            SnapshotResponse(
                href = "${minioProperties.endpoint}/${minioProperties.bucket}/$objectName",
                newUpdates = "http://localhost:8080/api/v1/updates?from=$timestamp",
            )
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    @GetMapping("/updates")
    fun getUpdatesFrom(from: String): UpdatesResponse = getSpeedLimitObjects(updateSuffix)
        .filter { it.timestamp > from }
        .sortedBy { it.timestamp }
        .let { updates ->
            UpdatesResponse(
                hrefs = updates.map { "${minioProperties.endpoint}/${minioProperties.bucket}/${it.objectName}" },
                newUpdates = "http://localhost:8080/api/v1/updates?from=${updates.lastOrNull()?.timestamp ?: from}",
            )
        }

    private data class SpeedLimitObject(
        val objectName: String,
        val timestamp: String,
    )

    private fun getSpeedLimitObjects(suffix: String): List<SpeedLimitObject> =
        minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(minioProperties.bucket)
                .prefix(speedLimitPrefix)
                .recursive(true)
                .build()
        )
            .map { it.get().objectName() }
            .filter { it.endsWith(suffix) }
            .map {
                SpeedLimitObject(
                    objectName = it,
                    timestamp = it.removePrefix(speedLimitPrefix).removeSuffix(suffix),
                )
            }
}
