package no.vegvesen.nvdb.tnits.common.infrastructure

import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.common.extensions.clear
import no.vegvesen.nvdb.tnits.common.extensions.delete
import no.vegvesen.nvdb.tnits.common.extensions.getOrNull
import no.vegvesen.nvdb.tnits.common.extensions.put
import no.vegvesen.nvdb.tnits.common.model.S3Config

class MinioGateway(private val minioClient: MinioClient, private val s3Config: S3Config) {
    fun put(objectKey: String, content: ByteArray) = minioClient.put(s3Config.bucket, objectKey, content)
    fun getOrNull(objectKey: String) = minioClient.getOrNull(s3Config.bucket, objectKey)
    fun delete(objectKey: String) = minioClient.delete(s3Config.bucket, objectKey)
    fun clear(prefix: String = "") = minioClient.clear(s3Config.bucket, prefix)
}
