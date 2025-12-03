package no.vegvesen.nvdb.tnits.common.extensions

import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.messages.DeleteObject
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

fun MinioClient.objectExists(bucket: String, objectName: String): Boolean = try {
    statObject(
        StatObjectArgs.builder()
            .bucket(bucket)
            .`object`(objectName)
            .build(),
    )
    true
} catch (e: ErrorResponseException) {
    if (e.errorResponse().code() == "NoSuchKey") {
        false
    } else {
        throw e
    }
}

fun MinioClient.listObjectNames(bucket: String, prefix: String, recursive: Boolean = true): List<String> = listObjects(
    ListObjectsArgs.builder()
        .bucket(bucket)
        .prefix(prefix)
        .recursive(recursive)
        .build(),
)
    .map { it.get().objectName() }
    .toList()

fun MinioClient.deleteMultiple(bucket: String, objects: List<String>) {
    val deleteResults = removeObjects(
        RemoveObjectsArgs.builder()
            .bucket(bucket)
            .objects(objects.map { DeleteObject(it) })
            .build(),
    )

    // Trigger deletion and collect any errors
    for (result in deleteResults) {
        result.get() // This throws if deletion failed
    }
}
