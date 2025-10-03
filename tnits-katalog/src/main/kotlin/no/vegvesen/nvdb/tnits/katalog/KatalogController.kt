package no.vegvesen.nvdb.tnits.katalog

import io.minio.ListObjectsArgs
import io.minio.MinioClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class KatalogController(private val minioClient: MinioClient, private val minioProperties: MinioProperties) {

    @GetMapping("/snapshots/latest")
    fun getLatestSnapshot(): SnapshotResponse {

        val prefix = "0105-speedLimit/"
        val suffix = "/snapshot.xml.gz"

        val (objectName, timestamp) = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(minioProperties.bucket)
                .prefix(prefix)
                .recursive(true)
                .build()
        )
            .map { it.get().objectName() }
            .filter { it.endsWith(suffix) }
            .map { it to it.removePrefix(prefix).removeSuffix(suffix) }
            .maxBy { it.second }

        return SnapshotResponse(
            href = "${minioProperties.endpoint}/${minioProperties.bucket}/$objectName",
            newUpdates = "http://localhost:8080/api/v1/updates?from=$timestamp",
        )
    }

    @GetMapping("/updates")
    fun getUpdatesFrom(from: String): UpdatesResponse {

        val prefix = "0105-speedLimit/"
        val suffix = "/update.xml.gz"

        val (objectNames, latestTimestamp) = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(minioProperties.bucket)
                .prefix(prefix)
                .recursive(true)
                .build()
        )
            .map { it.get().objectName() }
            .filter { it.endsWith(suffix) }
            .map { it to it.removePrefix(prefix).removeSuffix(suffix) }
            .filter { it.second > from }
            .sortedBy { it.second }
            .let { updates -> updates.map { it.first } to (updates.lastOrNull()?.second ?: from) }

        return UpdatesResponse(
            hrefs = objectNames.map { "${minioProperties.endpoint}/${minioProperties.bucket}/$it" },
            newUpdates = "http://localhost:8080/api/v1/updates?from=$latestTimestamp",
        )
    }
}
