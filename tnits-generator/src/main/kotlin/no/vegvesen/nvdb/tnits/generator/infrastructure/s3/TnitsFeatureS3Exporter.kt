package no.vegvesen.nvdb.tnits.generator.infrastructure.s3

import io.minio.MinioClient
import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.common.extensions.delete
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.common.model.S3Config
import no.vegvesen.nvdb.tnits.generator.config.ExporterConfig
import no.vegvesen.nvdb.tnits.generator.core.api.TnitsFeatureExporter
import no.vegvesen.nvdb.tnits.generator.core.extensions.truncateToSeconds
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsExportType
import java.io.OutputStream
import kotlin.time.Instant

@Singleton
class TnitsFeatureS3Exporter(
    private val exporterConfig: ExporterConfig,
    private val minioClient: MinioClient,
    private val s3Config: S3Config,
) : WithLogger, TnitsFeatureExporter {
    override fun openExportStream(timestamp: Instant, exportType: TnitsExportType, featureType: ExportedFeatureType): OutputStream {
        val objectKey = generateS3Key(timestamp, exportType, featureType)
        log.info("Lagrer $exportType eksport av $featureType til S3: s3://${s3Config.bucket}/$objectKey")

        return minioClient.openS3Stream(bucket = s3Config.bucket, objectKey, gzip = exporterConfig.gzip)
    }

    override fun deleteExport(timestamp: Instant, exportType: TnitsExportType, featureType: ExportedFeatureType) {
        val objectKey = generateS3Key(timestamp, exportType, featureType)
        log.info("Sletter $exportType eksport av $featureType fra S3: s3://${s3Config.bucket}/$objectKey")

        try {
            minioClient.delete(bucket = s3Config.bucket, objectKey)
        } catch (e: Exception) {
            log.error("Feil ved sletting av eksport $objectKey fra S3: ${e.localizedMessage}")
        }
    }

    private fun generateS3Key(timestamp: Instant, exportType: TnitsExportType, featureType: ExportedFeatureType): String =
        generateS3Key(timestamp, exportType, exporterConfig.gzip, featureType)

    companion object {

        fun generateS3Key(timestamp: Instant, exportType: TnitsExportType, gzip: Boolean, featureType: ExportedFeatureType): String {
            val typePrefix = featureType.getTypePrefix()
            val timestampStr = timestamp.truncateToSeconds().toString().replace(":", "-")
            val extension = if (gzip) ".xml.gz" else ".xml"
            return "$typePrefix/$timestampStr/${exportType.name.lowercase()}$extension"
        }
    }
}
