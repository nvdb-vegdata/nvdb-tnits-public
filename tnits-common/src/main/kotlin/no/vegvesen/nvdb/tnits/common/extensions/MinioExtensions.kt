package no.vegvesen.nvdb.tnits.common.extensions

import io.minio.*
import io.minio.errors.ErrorResponseException
import java.io.ByteArrayInputStream

fun MinioClient.getOrNull(bucket: String, objectKey: String): ByteArray? = try {
    getObject(
        GetObjectArgs.builder()
            .bucket(bucket)
            .`object`(objectKey)
            .build(),
    ).use { it.readBytes() }
} catch (e: ErrorResponseException) {
    if (e.errorResponse().code() == "NoSuchKey") {
        null
    } else {
        throw e
    }
}

fun MinioClient.put(bucket: String, objectKey: String, content: ByteArray) {
    putObject(
        PutObjectArgs.builder()
            .bucket(bucket)
            .`object`(objectKey)
            .stream(ByteArrayInputStream(content), content.size.toLong(), -1)
            .build(),
    )
}

fun MinioClient.delete(bucket: String, objectKey: String) {
    removeObject(
        RemoveObjectArgs.builder()
            .bucket(bucket)
            .`object`(objectKey)
            .build(),
    )
}

fun MinioClient.clear(bucket: String, prefix: String = "") {
    val objects = listObjects(
        ListObjectsArgs.builder()
            .bucket(bucket)
            .prefix(prefix)
            .recursive(true)
            .build(),
    )
    for (result in objects) {
        delete(bucket, result.get().objectName())
    }
}
