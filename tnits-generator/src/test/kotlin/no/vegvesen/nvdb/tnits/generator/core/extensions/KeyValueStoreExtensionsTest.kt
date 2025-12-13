package no.vegvesen.nvdb.tnits.generator.core.extensions

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import no.vegvesen.nvdb.tnits.generator.config.RocksDbConfig
import no.vegvesen.nvdb.tnits.generator.core.api.getValue
import no.vegvesen.nvdb.tnits.generator.core.api.putValue
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.KeyValueRocksDbStore
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.RocksDbContext
import java.io.File
import kotlin.io.path.createTempDirectory

class KeyValueStoreExtensionsTest :
    ShouldSpec({

        lateinit var tempDir: File
        lateinit var rocksDbConfig: RocksDbContext
        lateinit var store: KeyValueRocksDbStore

        beforeTest {
            tempDir = createTempDirectory("keyvalue-extensions-test").toFile()
            rocksDbConfig = RocksDbContext(RocksDbConfig(tempDir.absolutePath), enableCompression = false)
            store = KeyValueRocksDbStore(rocksDbConfig)
        }

        afterTest {
            rocksDbConfig.close()
            tempDir.deleteRecursively()
        }

        should("clear veglenkesekvens settings") {
            // Arrange
            store.putValue("veglenkesekvenser_setting1", "value1")
            store.putValue("veglenkesekvenser_setting2", "value2")
            store.putValue("other_setting", "value3")
            store.putValue("veglenkesekvenser_backfill_completed", true)

            // Act
            store.clearVeglenkesekvensSettings()

            // Assert
            store.getValue<String>("veglenkesekvenser_setting1") shouldBe null
            store.getValue<String>("veglenkesekvenser_setting2") shouldBe null
            store.getValue<Boolean>("veglenkesekvenser_backfill_completed") shouldBe null
            store.getValue<String>("other_setting") shouldBe "value3" // Should remain
        }
    })
