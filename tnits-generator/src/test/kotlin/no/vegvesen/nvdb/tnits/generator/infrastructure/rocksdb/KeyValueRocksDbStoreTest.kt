package no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import no.vegvesen.nvdb.tnits.generator.core.api.getValue
import no.vegvesen.nvdb.tnits.generator.core.api.putValue
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.KeyValueRocksDbStore
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.RocksDbContext
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.time.Instant

class KeyValueRocksDbStoreTest :
    ShouldSpec({

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

        should("store and retrieve string values") {
            store.putValue("test-key", "test-value")
            val result = store.getValue<String>("test-key")

            result shouldBe "test-value"
        }

        should("store and retrieve different data types") {
            store.putValue("string-key", "string-value")
            store.putValue("long-key", 123L)
            store.putValue("boolean-key", true)
            store.putValue("instant-key", Instant.parse("2023-01-01T12:00:00Z"))

            store.getValue<String>("string-key") shouldBe "string-value"
            store.getValue<Long>("long-key") shouldBe 123L
            store.getValue<Boolean>("boolean-key") shouldBe true
            store.getValue<Instant>("instant-key") shouldBe Instant.parse("2023-01-01T12:00:00Z")
        }

        should("return null for non-existent keys") {
            val result = store.getValue<String>("non-existent-key")
            result shouldBe null
        }

        should("delete keys") {
            store.putValue("key-to-delete", "value")
            store.getValue<String>("key-to-delete") shouldBe "value"

            store.delete("key-to-delete")
            store.getValue<String>("key-to-delete") shouldBe null
        }

        should("find keys by prefix") {
            store.putValue("prefix_key1", "value1")
            store.putValue("prefix_key2", "value2")
            store.putValue("other_key", "value3")
            store.putValue("prefix_another", "value4")

            val keys = store.findKeysByPrefix("prefix_")

            keys shouldHaveSize 3
            keys shouldContainExactlyInAnyOrder listOf("prefix_key1", "prefix_key2", "prefix_another")
        }

        should("count keys by prefix") {
            store.putValue("count_key1", "value1")
            store.putValue("count_key2", "value2")
            store.putValue("other_key", "value3")
            store.putValue("count_another", "value4")

            val count = store.countKeysByPrefix("count_")

            count shouldBe 3
        }

        should("count keys matching pattern with prefix and suffix") {
            store.putValue("range_1_completed", true)
            store.putValue("range_2_completed", true)
            store.putValue("range_3_started", false)
            store.putValue("range_4_completed", true)
            store.putValue("other_completed", true)

            val count = store.countKeysMatchingPattern("range_", "_completed")

            count shouldBe 3
        }

        should("delete keys by prefix") {
            store.putValue("delete_key1", "value1")
            store.putValue("delete_key2", "value2")
            store.putValue("keep_key", "value3")
            store.putValue("delete_another", "value4")

            store.deleteKeysByPrefix("delete_")

            store.getValue<String>("delete_key1") shouldBe null
            store.getValue<String>("delete_key2") shouldBe null
            store.getValue<String>("delete_another") shouldBe null
            store.getValue<String>("keep_key") shouldBe "value3"
        }

        should("handle empty prefix operations") {
            store.putValue("key1", "value1")
            store.putValue("key2", "value2")

            store.findKeysByPrefix("nonexistent_") shouldHaveSize 0
            store.countKeysByPrefix("nonexistent_") shouldBe 0
            store.countKeysMatchingPattern("nonexistent_", "_suffix") shouldBe 0
        }

        should("clear all data") {
            store.putValue("key1", "value1")
            store.putValue("key2", "value2")
            store.putValue("key3", "value3")

            store.size() shouldBe 3

            store.clear()

            store.size() shouldBe 0
            store.getValue<String>("key1") shouldBe null
            store.getValue<String>("key2") shouldBe null
            store.getValue<String>("key3") shouldBe null
        }

        should("handle serializable data classes") {
            @Serializable
            data class TestData(val id: Long, val name: String, val active: Boolean)

            val testData = TestData(123L, "test-name", true)

            store.putValue("data-key", testData)
            val result = store.getValue<TestData>("data-key")

            result shouldBe testData
        }

        should("update existing keys") {
            store.putValue("update-key", "original-value")
            store.getValue<String>("update-key") shouldBe "original-value"

            store.putValue("update-key", "updated-value")
            store.getValue<String>("update-key") shouldBe "updated-value"
        }
    })
