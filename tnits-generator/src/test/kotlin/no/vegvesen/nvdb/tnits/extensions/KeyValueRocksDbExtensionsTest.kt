package no.vegvesen.nvdb.tnits.extensions

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import no.vegvesen.nvdb.tnits.storage.KeyValueRocksDbStore
import no.vegvesen.nvdb.tnits.storage.RocksDbContext
import java.io.File
import kotlin.io.path.createTempDirectory

class KeyValueRocksDbExtensionsTest :
    ShouldSpec({

        lateinit var tempDir: File
        lateinit var rocksDbConfig: RocksDbContext
        lateinit var store: KeyValueRocksDbStore

        beforeTest {
            tempDir = createTempDirectory("keyvalue-extensions-test").toFile()
            rocksDbConfig = RocksDbContext(tempDir.absolutePath, enableCompression = false)
            store = KeyValueRocksDbStore(rocksDbConfig)
        }

        afterTest {
            rocksDbConfig.close()
            tempDir.deleteRecursively()
        }

        should("clear veglenkesekvens settings") {
            // Arrange
            store.put("veglenkesekvenser_setting1", "value1")
            store.put("veglenkesekvenser_setting2", "value2")
            store.put("other_setting", "value3")
            store.put("veglenkesekvenser_backfill_completed", true)

            // Act
            store.clearVeglenkesekvensSettings()

            // Assert
            store.get<String>("veglenkesekvenser_setting1") shouldBe null
            store.get<String>("veglenkesekvenser_setting2") shouldBe null
            store.get<Boolean>("veglenkesekvenser_backfill_completed") shouldBe null
            store.get<String>("other_setting") shouldBe "value3" // Should remain
        }

        should("count worker last ID entries") {
            // Arrange
            store.put("veglenkesekvenser_backfill_last_id_worker_0", 1000L)
            store.put("veglenkesekvenser_backfill_last_id_worker_1", 2000L)
            store.put("veglenkesekvenser_backfill_last_id_worker_2", 3000L)
            store.put("veglenkesekvenser_backfill_other", "value")
            store.put("other_key", "value")

            // Act
            val count = store.getWorkerLastIdCount()

            // Assert
            count shouldBe 3
        }

        should("count range worker completions") {
            // Arrange
            store.put("veglenkesekvenser_backfill_range_0_completed", true)
            store.put("veglenkesekvenser_backfill_range_1_completed", true)
            store.put("veglenkesekvenser_backfill_range_2_completed", true)
            store.put("veglenkesekvenser_backfill_range_0_started", false)
            store.put("veglenkesekvenser_backfill_range_1_started", false)
            store.put("other_completed", true)

            // Act
            val count = store.getRangeWorkerCount()

            // Assert
            count shouldBe 3
        }

        should("check if range is completed") {
            // Arrange
            store.put("veglenkesekvenser_backfill_range_0_completed", true)
            store.put("veglenkesekvenser_backfill_range_1_completed", false)

            // Act & Assert
            store.isRangeCompleted(0) shouldBe true
            store.isRangeCompleted(1) shouldBe false
            store.isRangeCompleted(2) shouldBe false // Non-existent should return false
        }

        should("mark range as completed") {
            // Arrange
            store.isRangeCompleted(0) shouldBe false

            // Act
            store.markRangeCompleted(0)

            // Assert
            store.isRangeCompleted(0) shouldBe true
            store.get<Boolean>("veglenkesekvenser_backfill_range_0_completed") shouldBe true
        }

        should("handle multiple worker ranges") {
            // Arrange - Mark several ranges as completed
            store.markRangeCompleted(0)
            store.markRangeCompleted(1)
            store.markRangeCompleted(3)

            // Act & Assert
            store.isRangeCompleted(0) shouldBe true
            store.isRangeCompleted(1) shouldBe true
            store.isRangeCompleted(2) shouldBe false
            store.isRangeCompleted(3) shouldBe true
            store.getRangeWorkerCount() shouldBe 3
        }

        should("work with complex scenarios") {
            // Arrange - Set up a complex backfill scenario
            store.put("veglenkesekvenser_backfill_started", "2023-01-01T00:00:00Z")
            store.put("veglenkesekvenser_backfill_last_id_worker_0", 1000L)
            store.put("veglenkesekvenser_backfill_last_id_worker_1", 2000L)
            store.markRangeCompleted(0)
            store.markRangeCompleted(1)
            store.put("veglenkesekvenser_other_setting", "some-value")

            // Act - Clear all veglenkesekvens settings
            store.clearVeglenkesekvensSettings()

            // Assert - All veglenkesekvenser_ keys should be gone
            store.get<String>("veglenkesekvenser_backfill_started") shouldBe null
            store.get<Long>("veglenkesekvenser_backfill_last_id_worker_0") shouldBe null
            store.get<Long>("veglenkesekvenser_backfill_last_id_worker_1") shouldBe null
            store.get<String>("veglenkesekvenser_other_setting") shouldBe null
            store.isRangeCompleted(0) shouldBe false
            store.isRangeCompleted(1) shouldBe false
            store.getWorkerLastIdCount() shouldBe 0
            store.getRangeWorkerCount() shouldBe 0
        }
    })
