package no.vegvesen.nvdb.tnits.generator.infrastructure.s3

import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.messages.Item
import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.common.model.S3Config
import no.vegvesen.nvdb.tnits.common.utils.parseTimestampFromS3Key
import no.vegvesen.nvdb.tnits.generator.config.ExporterConfig
import no.vegvesen.nvdb.tnits.generator.core.api.TimestampService
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsExportType
import kotlin.time.Instant

@Singleton
class S3TimestampService(private val minioClient: MinioClient, exporterConfig: ExporterConfig, s3Config: S3Config) :
    TimestampService,
    WithLogger {

    private val bucketName = s3Config.bucket

    override fun getLastSnapshotTimestamp(featureType: ExportedFeatureType): Instant? = getLastExportTimestamp(featureType, TnitsExportType.Snapshot)

    override fun getLastUpdateTimestamp(featureType: ExportedFeatureType): Instant? = getLastExportTimestamp(featureType, TnitsExportType.Update)

    override fun findAllSnapshotTimestamps(featureType: ExportedFeatureType): List<Instant> = try {
        val typePrefix = featureType.getTypePrefix() + "/"

        log.debug("Searching for all snapshot exports in S3 bucket: {}, prefix: {}", bucketName, typePrefix)

        val exports = listExportsByType(typePrefix, TnitsExportType.Snapshot)
        val timestamps = exports
            .mapNotNull { parseTimestampFromS3Key(it.objectName()) }
            .sorted()

        log.debug("Found {} snapshot exports with valid timestamps", timestamps.size)

        timestamps
    } catch (e: Exception) {
        log.warn("Failed to retrieve snapshot timestamps from S3")
        throw e
    }

    override fun findAllUpdateTimestamps(featureType: ExportedFeatureType): List<Instant> = try {
        val typePrefix = featureType.getTypePrefix() + "/"

        log.debug("Searching for all update exports in S3 bucket: {}, prefix: {}", bucketName, typePrefix)

        val exports = listExportsByType(typePrefix, TnitsExportType.Update)
        val timestamps = exports
            .mapNotNull { parseTimestampFromS3Key(it.objectName()) }
            .sorted()

        log.debug("Found {} update exports with valid timestamps", timestamps.size)

        timestamps
    } catch (e: Exception) {
        log.warn("Failed to retrieve update timestamps from S3")
        throw e
    }

    private fun getLastExportTimestamp(featureType: ExportedFeatureType, exportType: TnitsExportType): Instant? = try {
        val typePrefix = featureType.getTypePrefix() + "/"

        log.debug("Searching for last {} export in S3 bucket: {}, prefix: {}", exportType.name.lowercase(), bucketName, typePrefix)

        val exports = listExportsByType(typePrefix, exportType)
        val latestTimestamp = exports
            .mapNotNull { parseTimestampFromS3Key(it.objectName()) }
            .maxOrNull()

        if (latestTimestamp == null && exports.isNotEmpty()) {
            log.warn("Found ${exports.size} ${exportType.name.lowercase()} exports but could not parse any timestamps")
        } else {
            log.debug("Found {} {} exports, latest timestamp: {}", exports.size, exportType.name.lowercase(), latestTimestamp)
        }

        latestTimestamp
    } catch (e: Exception) {
        log.warn("Failed to retrieve ${exportType.name.lowercase()} timestamp from S3")
        throw e
    }

    private fun listExportsByType(prefix: String, exportType: TnitsExportType): List<Item> {
        val exportTypeString = exportType.name.lowercase()
        val results = mutableListOf<Item>()

        val listObjectsArgs = ListObjectsArgs.builder()
            .bucket(bucketName)
            .prefix(prefix)
            .recursive(true)
            .build()

        val objects = minioClient.listObjects(listObjectsArgs)

        for (result in objects) {
            val item = result.get()
            val objectName = item.objectName()

            // Filter for the specific export type (snapshot or update)
            if (objectName.contains("/$exportTypeString.xml")) {
                results.add(item)
            }
        }

        return results
    }
}
