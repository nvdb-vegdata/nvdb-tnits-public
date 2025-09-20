package no.vegvesen.nvdb.tnits.services

import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.messages.Item
import no.vegvesen.nvdb.tnits.SpeedLimitExporter
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import kotlin.time.Instant

class S3TimestampService(private val minioClient: MinioClient, private val bucketName: String) : WithLogger {

    fun getLastSnapshotTimestamp(vegobjekttype: Int = 105): Instant? = getLastExportTimestamp(vegobjekttype, SpeedLimitExporter.ExportType.Snapshot)

    fun getLastUpdateTimestamp(vegobjekttype: Int = 105): Instant? = getLastExportTimestamp(vegobjekttype, SpeedLimitExporter.ExportType.Update)

    private fun getLastExportTimestamp(vegobjekttype: Int, exportType: SpeedLimitExporter.ExportType): Instant? = try {
        val paddedType = vegobjekttype.toString().padStart(4, '0')
        val prefix = "$paddedType-speed-limits/"

        log.debug("Searching for last ${exportType.name.lowercase()} export in S3 bucket: $bucketName, prefix: $prefix")

        val exports = listExportsByType(prefix, exportType)
        val latestTimestamp = exports
            .mapNotNull { parseTimestampFromS3Key(it.objectName()) }
            .maxOrNull()

        if (latestTimestamp == null && exports.isNotEmpty()) {
            log.warn("Found ${exports.size} ${exportType.name.lowercase()} exports but could not parse any timestamps")
        } else {
            log.debug("Found ${exports.size} ${exportType.name.lowercase()} exports, latest timestamp: $latestTimestamp")
        }

        latestTimestamp
    } catch (e: Exception) {
        log.warn("Failed to retrieve ${exportType.name.lowercase()} timestamp from S3, falling back to RocksDB timestamps", e)
        null
    }

    private fun listExportsByType(prefix: String, exportType: SpeedLimitExporter.ExportType): List<Item> {
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

    fun parseTimestampFromS3Key(s3Key: String): Instant? {
        return try {
            // Expected format: 0105-speed-limits/2025-01-15T10-30-00Z/snapshot.xml.gz
            // Extract timestamp using regex: YYYY-MM-DDTHH-mm-ssZ
            val timestampRegex = Regex("""(\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}Z)""")
            val matchResult = timestampRegex.find(s3Key)
                ?: return null.also { log.debug("No timestamp found in S3 key: $s3Key") }

            val timestampStr = matchResult.groupValues[1]
            // Convert S3-safe format (2025-01-15T10-30-00Z) to ISO format (2025-01-15T10:30:00Z)
            val isoTimestamp = timestampStr.replace(Regex("""T(\d{2})-(\d{2})-(\d{2})Z"""), "T$1:$2:$3Z")

            val parsed = Instant.parse(isoTimestamp)
            log.debug("Successfully parsed timestamp '{}' from S3 key: {}", parsed, s3Key)
            parsed
        } catch (e: Exception) {
            log.debug("Failed to parse timestamp from S3 key: $s3Key", e)
            null
        }
    }
}
