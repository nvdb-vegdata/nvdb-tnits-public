package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class NodePortCountRocksDbStoreTest : StringSpec() {
    private lateinit var tempDir: String
    private lateinit var configuration: RocksDbConfiguration
    private lateinit var store: NodePortCountRocksDbStore

    override suspend fun beforeSpec(spec: Spec) {
        tempDir = Files.createTempDirectory("rocksdb-nodes-test").toString()
        configuration = RocksDbConfiguration(tempDir, enableCompression = true)
        store =
            NodePortCountRocksDbStore(
                configuration.getDatabase(),
                configuration.getNoderColumnFamily(),
            )
    }

    override suspend fun beforeEach(testCase: TestCase) {
        configuration.clear()
        // Recreate the store with fresh references after clearing
        store =
            NodePortCountRocksDbStore(
                configuration.getDatabase(),
                configuration.getNoderColumnFamily(),
            )
    }

    override suspend fun afterSpec(spec: Spec) {
        configuration.close()
        java.io.File(tempDir).deleteRecursively()
    }

    init {
        "should store and retrieve node port counts" {
            store.upsert(100L, 4)
            store.upsert(200L, 2)

            store.get(100L) shouldBe 4
            store.get(200L) shouldBe 2
            store.get(300L) shouldBe null
            store.size() shouldBe 2L

            val batchResult = store.batchGet(listOf(100L, 200L, 300L))
            batchResult.size shouldBe 2
            batchResult[100L] shouldBe 4
            batchResult[200L] shouldBe 2

            store.delete(100L)
            store.get(100L) shouldBe null
        }

        "should perform batch operations" {
            val updates =
                mapOf(
                    100L to 4,
                    200L to 2,
                    300L to null,
                )

            store.batchUpdate(updates)

            store.get(100L) shouldBe 4
            store.get(200L) shouldBe 2
            store.get(300L) shouldBe null

            val batchResult = store.batchGet(listOf(100L, 200L, 300L, 400L))
            batchResult.size shouldBe 2
            batchResult[100L] shouldBe 4
            batchResult[200L] shouldBe 2
        }
    }
}
