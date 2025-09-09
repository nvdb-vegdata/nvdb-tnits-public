package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files

class RocksDbConfigurationTest : StringSpec() {
    private lateinit var tempDir: String
    private lateinit var configuration: RocksDbConfiguration

    override suspend fun beforeSpec(spec: Spec) {
        tempDir = Files.createTempDirectory("rocksdb-config-test").toString()
        configuration = RocksDbConfiguration(tempDir, enableCompression = true)
    }

    override suspend fun beforeEach(testCase: TestCase) {
        configuration.clear()
    }

    override suspend fun afterSpec(spec: Spec) {
        configuration.close()
        java.io.File(tempDir).deleteRecursively()
    }

    init {
        "should initialize RocksDB with column families" {
            configuration.getDatabase() shouldNotBe null
            configuration.getColumnFamily(ColumnFamily.DEFAULT).shouldNotBeNull()
            configuration.getColumnFamily(ColumnFamily.NODER).shouldNotBeNull()

            configuration.getTotalSize() shouldBe 0L
            configuration.existsAndHasData() shouldBe false
        }

        "should clear database" {
            val veglenkerStore = VeglenkerRocksDbStore(configuration)

            veglenkerStore.upsert(1L, emptyList())

            configuration.getTotalSize() shouldBe 1L
            configuration.existsAndHasData() shouldBe true

            configuration.clear()

            configuration.getTotalSize() shouldBe 0L
            configuration.existsAndHasData() shouldBe false
        }
    }
}
