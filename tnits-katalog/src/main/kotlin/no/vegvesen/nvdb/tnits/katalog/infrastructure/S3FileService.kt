package no.vegvesen.nvdb.tnits.katalog.infrastructure

import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.common.model.RoadFeatureTypeCode
import no.vegvesen.nvdb.tnits.common.utils.parseTimestampFromS3Key
import no.vegvesen.nvdb.tnits.katalog.config.MinioProperties
import no.vegvesen.nvdb.tnits.katalog.core.api.FileService
import no.vegvesen.nvdb.tnits.katalog.core.model.FileDownload
import no.vegvesen.nvdb.tnits.katalog.core.model.FileObject
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import kotlin.time.toJavaInstant

@Component
class S3FileService(private val minioClient: MinioClient, private val minioProperties: MinioProperties) : FileService {
    override fun getFileObjects(type: RoadFeatureTypeCode, suffix: String): List<FileObject> {
        val prefix = getPrefix(type)
        return minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(minioProperties.bucket)
                .prefix(prefix)
                .recursive(true)
                .build(),
        )
            .map { it.get() }
            .filter { it.objectName().endsWith(suffix) }
            .mapNotNull { item ->
                parseTimestampFromS3Key(item.objectName()).let {
                    if (it == null) {
                        println("Warning: Could not parse timestamp from S3 key: ${item.objectName()}")
                        null
                    } else {
                        FileObject(
                            objectName = item.objectName(),
                            timestamp = it.toJavaInstant(),
                            size = item.size(),
                        )
                    }
                }
            }
    }

    override fun downloadFile(objectName: String): FileDownload {
        val fileName = objectName.replace('/', '_')
        val contentType = determineContentType(objectName)
        val size = getFileSize(objectName)
        val inputStream = getInputStream(objectName)

        return FileDownload(
            inputStream = inputStream,
            fileName = fileName,
            contentType = contentType,
            size = size,
        )
    }

    private fun getFileSize(objectName: String): Long = try {
        minioClient.statObject(
            StatObjectArgs.builder()
                .bucket(minioProperties.bucket)
                .`object`(objectName)
                .build(),
        ).size()
    } catch (e: ErrorResponseException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $objectName", e)
    } catch (e: Exception) {
        throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error getting file metadata: $objectName", e)
    }

    private fun getInputStream(objectName: String) = try {
        minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(minioProperties.bucket)
                .`object`(objectName)
                .build(),
        )
    } catch (e: ErrorResponseException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $objectName", e)
    } catch (e: Exception) {
        throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error downloading file: $objectName", e)
    }

    private fun determineContentType(path: String): String = when {
        path.endsWith(".xml.gz") -> "application/gzip"
        path.endsWith(".xml") -> "application/xml"
        else -> "application/octet-stream"
    }

    companion object {
        fun getPrefix(type: RoadFeatureTypeCode) = ExportedFeatureType.from(type).getTypePrefix()
    }
}
