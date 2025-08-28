package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

class NodeStorageTest :
    StringSpec({

        "should store and retrieve node port counts" {
            val tempDir = Files.createTempDirectory("rocksdb-node-test").toString()
            val store = RocksDbVeglenkerStore(tempDir, enableCompression = false)

            try {
                // Test upsert node port count
                store.upsertNodePortCount(1001L, 3)
                store.upsertNodePortCount(1002L, 5)

                // Test get individual node port count
                store.getNodePortCount(1001L) shouldBe 3
                store.getNodePortCount(1002L) shouldBe 5
                store.getNodePortCount(9999L) shouldBe null

                // Test batch get node port counts
                val batchResult = store.batchGetNodePortCounts(listOf(1001L, 1002L, 9999L))
                batchResult[1001L] shouldBe 3
                batchResult[1002L] shouldBe 5
                batchResult[9999L] shouldBe null

                // Test batch update
                val updates =
                    mapOf(
                        1001L to 4, // update existing
                        1003L to 2, // new node
                        1002L to null, // delete existing
                    )
                store.batchUpdateNodePortCounts(updates)

                store.getNodePortCount(1001L) shouldBe 4
                store.getNodePortCount(1002L) shouldBe null
                store.getNodePortCount(1003L) shouldBe 2

                // Test delete
                store.deleteNodePortCount(1001L)
                store.getNodePortCount(1001L) shouldBe null
            } finally {
                store.close()
                File(tempDir).deleteRecursively()
            }
        }
    })
