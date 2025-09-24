package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.time.Instant

class KeyValueRocksDbStoreTest :
    StringSpec({

        lateinit var tempDir: File
        lateinit var rocksDbConfig: RocksDbContext
        lateinit var store: KeyValueRocksDbStore

        beforeTest {
            tempDir = createTempDirectory("keyvalue-test").toFile()
            rocksDbConfig = RocksDbContext(tempDir.absolutePath, enableCompression = false)
            store = KeyValueRocksDbStore(rocksDbConfig)
        }

        afterTest {
            rocksDbConfig.close()
            tempDir.deleteRecursively()
        }

        "should store and retrieve string values" {
            store.put("test-key", "test-value")
            val result = store.get<String>("test-key")

            result shouldBe "test-value"
        }

        "should store and retrieve different data types" {
            store.put("string-key", "string-value")
            store.put("long-key", 123L)
            store.put("boolean-key", true)
            store.put("instant-key", Instant.parse("2023-01-01T12:00:00Z"))

            store.get<String>("string-key") shouldBe "string-value"
            store.get<Long>("long-key") shouldBe 123L
            store.get<Boolean>("boolean-key") shouldBe true
            store.get<Instant>("instant-key") shouldBe Instant.parse("2023-01-01T12:00:00Z")
        }

        "should return null for non-existent keys" {
            val result = store.get<String>("non-existent-key")
            result shouldBe null
        }

        "should delete keys" {
            store.put("key-to-delete", "value")
            store.get<String>("key-to-delete") shouldBe "value"

            store.delete("key-to-delete")
            store.get<String>("key-to-delete") shouldBe null
        }

        "should find keys by prefix" {
            store.put("prefix_key1", "value1")
            store.put("prefix_key2", "value2")
            store.put("other_key", "value3")
            store.put("prefix_another", "value4")

            val keys = store.findKeysByPrefix("prefix_")

            keys shouldHaveSize 3
            keys shouldContainExactlyInAnyOrder listOf("prefix_key1", "prefix_key2", "prefix_another")
        }

        "should count keys by prefix" {
            store.put("count_key1", "value1")
            store.put("count_key2", "value2")
            store.put("other_key", "value3")
            store.put("count_another", "value4")

            val count = store.countKeysByPrefix("count_")

            count shouldBe 3
        }

        "should count keys matching pattern with prefix and suffix" {
            store.put("range_1_completed", true)
            store.put("range_2_completed", true)
            store.put("range_3_started", false)
            store.put("range_4_completed", true)
            store.put("other_completed", true)

            val count = store.countKeysMatchingPattern("range_", "_completed")

            count shouldBe 3
        }

        "should delete keys by prefix" {
            store.put("delete_key1", "value1")
            store.put("delete_key2", "value2")
            store.put("keep_key", "value3")
            store.put("delete_another", "value4")

            store.deleteKeysByPrefix("delete_")

            store.get<String>("delete_key1") shouldBe null
            store.get<String>("delete_key2") shouldBe null
            store.get<String>("delete_another") shouldBe null
            store.get<String>("keep_key") shouldBe "value3"
        }

        "should handle empty prefix operations" {
            store.put("key1", "value1")
            store.put("key2", "value2")

            store.findKeysByPrefix("nonexistent_") shouldHaveSize 0
            store.countKeysByPrefix("nonexistent_") shouldBe 0
            store.countKeysMatchingPattern("nonexistent_", "_suffix") shouldBe 0
        }

        "should clear all data" {
            store.put("key1", "value1")
            store.put("key2", "value2")
            store.put("key3", "value3")

            store.size() shouldBe 3

            store.clear()

            store.size() shouldBe 0
            store.get<String>("key1") shouldBe null
            store.get<String>("key2") shouldBe null
            store.get<String>("key3") shouldBe null
        }

        "should handle serializable data classes" {
            @Serializable
            data class TestData(val id: Long, val name: String, val active: Boolean)

            val testData = TestData(123L, "test-name", true)

            store.put("data-key", testData)
            val result = store.get<TestData>("data-key")

            result shouldBe testData
        }

        "should update existing keys" {
            store.put("update-key", "original-value")
            store.get<String>("update-key") shouldBe "original-value"

            store.put("update-key", "updated-value")
            store.get<String>("update-key") shouldBe "updated-value"
        }
    })
