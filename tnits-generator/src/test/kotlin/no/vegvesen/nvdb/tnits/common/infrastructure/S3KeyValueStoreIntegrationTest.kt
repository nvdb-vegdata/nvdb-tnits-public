package no.vegvesen.nvdb.tnits.common.infrastructure

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.RemoveObjectArgs
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import no.vegvesen.nvdb.tnits.common.MinioTestHelper
import no.vegvesen.nvdb.tnits.common.api.getValue
import no.vegvesen.nvdb.tnits.common.api.putValue
import no.vegvesen.nvdb.tnits.common.model.S3Config
import org.testcontainers.containers.MinIOContainer

class S3KeyValueStoreIntegrationTest : ShouldSpec() {

    private val minioContainer: MinIOContainer = MinioTestHelper.createMinioContainer()
    private lateinit var minioClient: MinioClient
    private val testBucket = "nvdb-tnits-test"
    private val s3Config = S3Config(
        endpoint = "",
        accessKey = "",
        secretKey = "",
        bucket = testBucket,
    )

    private lateinit var store: S3KeyValueStore

    @Serializable
    data class TestData(val name: String, val value: Int)

    init {
        beforeSpec {
            minioContainer.start()
            minioClient = MinioTestHelper.createMinioClient(minioContainer)
            MinioTestHelper.waitForMinioReady(minioClient)
            MinioTestHelper.ensureBucketExists(minioClient, testBucket)
            store = S3KeyValueStore(MinioGateway(minioClient, s3Config))
        }

        afterSpec {
            minioContainer.stop()
        }

        beforeEach {
            // Clear all objects from test bucket before each test
            val objects = minioClient.listObjects(ListObjectsArgs.builder().bucket(testBucket).build())
            for (result in objects) {
                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(testBucket)
                        .`object`(result.get().objectName())
                        .build(),
                )
            }
        }

        should("return null when key does not exist") {
            // Act
            val result = store.get("non-existent", serializer<String>())

            // Assert
            result shouldBe null
        }

        should("store and retrieve Boolean value") {
            // Arrange
            val key = "boolean-flag"
            val value = true

            // Act
            store.put(key, value, serializer())
            val result = store.get(key, serializer<Boolean>())

            // Assert
            result shouldBe value
        }

        should("store and retrieve String value") {
            // Arrange
            val key = "string-key"
            val value = "Hello, S3 Store!"

            // Act
            store.put(key, value, serializer())
            val result = store.get(key, serializer<String>())

            // Assert
            result shouldBe value
        }

        should("store and retrieve Int value") {
            // Arrange
            val key = "int-key"
            val value = 42

            // Act
            store.put(key, value, serializer())
            val result = store.get(key, serializer<Int>())

            // Assert
            result shouldBe value
        }

        should("store and retrieve custom serializable object") {
            // Arrange
            val key = "custom-object"
            val value = TestData("test", 123)

            // Act
            store.put(key, value, serializer())
            val result = store.get(key, serializer<TestData>())

            // Assert
            result shouldBe value
        }

        should("delete existing key") {
            // Arrange
            val key = "key-to-delete"
            store.put(key, "value", serializer())
            store.get(key, serializer<String>()) shouldBe "value"

            // Act
            store.delete(key)

            // Assert
            store.get(key, serializer<String>()) shouldBe null
        }

        should("be idempotent when deleting non-existent key") {
            // Arrange
            val key = "non-existent-key"

            // Act - should not throw exception
            store.delete(key)

            // Assert
            store.get(key, serializer<String>()) shouldBe null
        }

        should("clear all keys") {
            // Arrange
            store.put("key1", "value1", serializer())
            store.put("key2", "value2", serializer())
            store.put("key3", "value3", serializer())

            // Act
            store.clear()

            // Assert
            store.get("key1", serializer<String>()) shouldBe null
            store.get("key2", serializer<String>()) shouldBe null
            store.get("key3", serializer<String>()) shouldBe null
        }

        should("use getValue extension method") {
            // Arrange
            val key = "extension-key"
            val value = "extension-value"
            store.put(key, value, serializer())

            // Act
            val result = store.getValue<String>(key)

            // Assert
            result shouldBe value
        }

        should("use putValue extension method") {
            // Arrange
            val key = "extension-put-key"
            val value = 999

            // Act
            store.putValue(key, value)

            // Assert
            store.getValue<Int>(key) shouldBe value
        }

        should("work with flag-like Boolean usage") {
            // Arrange
            val flagKey = "feature-enabled"

            // Act - set flag
            store.putValue(flagKey, true)

            // Assert - check flag
            store.getValue<Boolean>(flagKey) shouldBe true

            // Act - clear flag
            store.delete(flagKey)

            // Assert - flag is gone
            store.getValue<Boolean>(flagKey) shouldBe null
        }

        should("store keys with correct S3 prefix") {
            // Arrange
            val key = "test-key"
            val value = "test-value"

            // Act
            store.putValue(key, value)

            // Assert - verify object exists with correct prefix
            val objects = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(testBucket)
                    .prefix("admin-flags/")
                    .build(),
            ).map { it.get().objectName() }.toList()

            objects shouldContain "admin-flags/test-key"
        }

        should("overwrite existing key with new value") {
            // Arrange
            val key = "overwrite-key"
            store.putValue(key, "old-value")

            // Act
            store.putValue(key, "new-value")

            // Assert
            store.getValue<String>(key) shouldBe "new-value"
        }

        should("handle empty string values") {
            // Arrange
            val key = "empty-string-key"
            val value = ""

            // Act
            store.putValue(key, value)

            // Assert
            store.getValue<String>(key) shouldBe value
        }

        should("return null and log error when deserialization fails") {
            // Arrange
            val key = "corrupted-data"
            val invalidJson = "{this is not valid JSON}"

            // Manually put invalid JSON directly to S3
            minioClient.putObject(
                io.minio.PutObjectArgs.builder()
                    .bucket(testBucket)
                    .`object`("admin-flags/$key")
                    .stream(
                        java.io.ByteArrayInputStream(invalidJson.toByteArray()),
                        invalidJson.length.toLong(),
                        -1,
                    )
                    .build(),
            )

            // Act - try to deserialize as Int
            val result = store.getValue<Int>(key)

            // Assert - should return null instead of throwing exception
            result shouldBe null
        }

        should("return null when JSON type does not match expected type") {
            // Arrange
            val key = "type-mismatch"
            // Store a String value
            store.putValue(key, "this is a string")

            // Act - try to retrieve as Int
            val result = store.getValue<Int>(key)

            // Assert - should return null due to type mismatch
            result shouldBe null
        }
    }
}
