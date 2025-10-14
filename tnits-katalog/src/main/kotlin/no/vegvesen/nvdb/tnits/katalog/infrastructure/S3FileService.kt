package no.vegvesen.nvdb.tnits.katalog.infrastructure

import io.minio.ListObjectsArgs
import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.common.model.RoadFeatureTypeCode
import no.vegvesen.nvdb.tnits.katalog.config.MinioProperties
import no.vegvesen.nvdb.tnits.katalog.core.api.FileService
import no.vegvesen.nvdb.tnits.katalog.core.model.FileObject
import org.springframework.stereotype.Component
import kotlin.time.Instant

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
            .map { it.get().objectName() }
            .filter { it.endsWith(suffix) }
            .map {
                FileObject(
                    objectName = it,
                    timestamp = it.removePrefix(prefix).removeSuffix(suffix).let { Instant.parse(it) },
                )
            }
    }

    companion object {
        fun getPrefix(type: RoadFeatureTypeCode) = ExportedFeatureType.from(type).getTypePrefix()
    }
}
