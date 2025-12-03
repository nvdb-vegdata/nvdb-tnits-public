package no.vegvesen.nvdb.tnits.katalog.infrastructure

import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import no.vegvesen.nvdb.tnits.common.extensions.delete
import no.vegvesen.nvdb.tnits.common.extensions.deleteMultiple
import no.vegvesen.nvdb.tnits.common.extensions.listObjectNames
import no.vegvesen.nvdb.tnits.common.extensions.objectExists
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.common.utils.parseTimestampFromS3Key
import no.vegvesen.nvdb.tnits.katalog.config.MinioProperties
import no.vegvesen.nvdb.tnits.katalog.core.api.FileService
import no.vegvesen.nvdb.tnits.katalog.core.exceptions.ClientException
import no.vegvesen.nvdb.tnits.katalog.core.exceptions.NotFoundException
import no.vegvesen.nvdb.tnits.katalog.core.model.FileDownload
import no.vegvesen.nvdb.tnits.katalog.core.model.FileObject
import org.springframework.stereotype.Component
import kotlin.time.toJavaInstant

@Component
class S3FileService(private val minioClient: MinioClient, private val minioProperties: MinioProperties) : FileService {
    override fun getFileObjects(type: ExportedFeatureType, suffix: String): List<FileObject> {
        val prefix = type.getTypePrefix()
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

    override fun delete(path: String, recursive: Boolean): List<String> {
        try {
            if (!recursive) {
                // Non-recursive mode: only delete exact matches
                if (minioClient.objectExists(minioProperties.bucket, path)) {
                    minioClient.delete(minioProperties.bucket, path)
                    return listOf(path)
                } else {
                    throw NotFoundException("Object not found: $path")
                }
            } else {
                // Recursive mode: delete all objects with prefix
                val objects = minioClient.listObjectNames(minioProperties.bucket, path)

                if (objects.isEmpty()) {
                    throw NotFoundException("No objects found with prefix: $path")
                }

                minioClient.deleteMultiple(minioProperties.bucket, objects)

                return objects
            }
        } catch (e: NotFoundException) {
            throw e
        } catch (e: ClientException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Error deleting objects at $path: ${e.message}", e)
        }
    }

    override fun list(path: String, recursive: Boolean): List<String> {
        try {
            val normalizedPath = if (path.isEmpty()) "" else path.trimEnd('/') + "/"
            val objects = minioClient.listObjectNames(minioProperties.bucket, normalizedPath, recursive)

            return if (recursive) {
                objects
            } else {
                objects.mapNotNull { objectName ->
                    val relativePath = objectName.removePrefix(normalizedPath)
                    val nextSlash = relativePath.indexOf('/')

                    if (nextSlash == -1) {
                        objectName
                    } else {
                        normalizedPath + relativePath.substring(0, nextSlash + 1)
                    }
                }.distinct()
            }
        } catch (e: Exception) {
            throw RuntimeException("Error listing objects at $path: ${e.message}", e)
        }
    }

    private fun getFileSize(objectName: String): Long = try {
        minioClient.statObject(
            StatObjectArgs.builder()
                .bucket(minioProperties.bucket)
                .`object`(objectName)
                .build(),
        ).size()
    } catch (e: ErrorResponseException) {
        throw NotFoundException("File not found: $objectName")
    } catch (e: Exception) {
        throw RuntimeException("Error getting file metadata for $objectName: ${e.message}", e)
    }

    private fun getInputStream(objectName: String) = try {
        minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(minioProperties.bucket)
                .`object`(objectName)
                .build(),
        )
    } catch (e: ErrorResponseException) {
        throw NotFoundException("File not found: $objectName")
    } catch (e: Exception) {
        throw RuntimeException("Error downloading file $objectName: ${e.message}", e)
    }

    private fun determineContentType(path: String): String = when {
        path.endsWith(".xml.gz") -> "application/gzip"
        path.endsWith(".xml") -> "application/xml"
        else -> "application/octet-stream"
    }
}
