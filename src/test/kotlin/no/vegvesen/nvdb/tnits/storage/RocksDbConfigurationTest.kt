package no.vegvesen.nvdb.tnits.storage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
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
            configuration.getDefaultColumnFamily() shouldNotBe null
            configuration.getNoderColumnFamily() shouldNotBe null

            configuration.getTotalSize() shouldBe 0L
            configuration.existsAndHasData() shouldBe false
        }

        "should clear database" {
            val veglenkerStore =
                VeglenkerRocksDbStore(
                    configuration.getDatabase(),
                    configuration.getVeglenkerColumnFamily(),
                )

            veglenkerStore.upsert(1L, emptyList())

            configuration.getTotalSize() shouldBe 1L
            configuration.existsAndHasData() shouldBe true

            configuration.clear()

            configuration.getTotalSize() shouldBe 0L
            configuration.existsAndHasData() shouldBe false
        }

        "should provide access to column families by name and enum" {
            val defaultCF = configuration.getColumnFamily(ColumnFamily.DEFAULT)
            val noderCF = configuration.getColumnFamily(ColumnFamily.NODER)
            val veglenkerCF = configuration.getColumnFamily(ColumnFamily.VEGLENKER)

            defaultCF shouldBe configuration.getDefaultColumnFamily()
            noderCF shouldBe configuration.getNoderColumnFamily()
            veglenkerCF shouldBe configuration.getVeglenkerColumnFamily()

            String(defaultCF.name) shouldBe "default"
            String(noderCF.name) shouldBe "noder"
            String(veglenkerCF.name) shouldBe "veglenker"

            // Test string-based access as well
            configuration.getColumnFamily("default") shouldBe defaultCF
            configuration.getColumnFamily("noder") shouldBe noderCF
            configuration.getColumnFamily("veglenker") shouldBe veglenkerCF
        }

        "should throw exception for unknown column family" {
            shouldThrow<IllegalArgumentException> {
                configuration.getColumnFamily("unknown")
            }
        }
    }
}
