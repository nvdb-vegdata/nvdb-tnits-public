package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files

class RocksDbContextTest : ShouldSpec() {
    private lateinit var tempDir: String
    private lateinit var dbContext: RocksDbContext

    override suspend fun beforeSpec(spec: Spec) {
        tempDir = Files.createTempDirectory("rocksdb-config-test").toString()
        dbContext = RocksDbContext(tempDir, enableCompression = true)
    }

    override suspend fun beforeEach(testCase: TestCase) {
        dbContext.clear()
    }

    override suspend fun afterSpec(spec: Spec) {
        dbContext.close()
        java.io.File(tempDir).deleteRecursively()
    }

    init {
        should("initialize RocksDB with column families") {
            dbContext.getDatabase() shouldNotBe null
            dbContext.getColumnFamily(ColumnFamily.DEFAULT).shouldNotBeNull()
            dbContext.getColumnFamily(ColumnFamily.NODER).shouldNotBeNull()

            dbContext.getTotalSize() shouldBe 0L
            dbContext.existsAndHasData() shouldBe false
        }

        should("clear database") {
            val veglenkerStore = VeglenkerRocksDbStore(dbContext)

            veglenkerStore.upsert(1L, emptyList())

            dbContext.getTotalSize() shouldBe 1L
            dbContext.existsAndHasData() shouldBe true

            dbContext.clear()

            dbContext.getTotalSize() shouldBe 0L
            dbContext.existsAndHasData() shouldBe false
        }

        should("find keys by prefix") {
            // Arrange
            val testData = mapOf(
                "user:1:name".toByteArray() to "Alice".toByteArray(),
                "user:1:email".toByteArray() to "alice@example.com".toByteArray(),
                "user:2:name".toByteArray() to "Bob".toByteArray(),
                "user:2:email".toByteArray() to "bob@example.com".toByteArray(),
                "post:1:title".toByteArray() to "Hello World".toByteArray(),
                "post:2:title".toByteArray() to "Another Post".toByteArray(),
            )

            testData.forEach { (key, value) ->
                dbContext.put(ColumnFamily.DEFAULT, key, value)
            }

            // Act
            val userKeys = dbContext.findKeysByPrefix(ColumnFamily.DEFAULT, "user:".toByteArray())
            val postKeys = dbContext.findKeysByPrefix(ColumnFamily.DEFAULT, "post:".toByteArray())
            val user1Keys = dbContext.findKeysByPrefix(ColumnFamily.DEFAULT, "user:1:".toByteArray())

            // Assert
            userKeys shouldHaveSize 4
            userKeys.map { String(it) } shouldContain "user:1:name"
            userKeys.map { String(it) } shouldContain "user:1:email"
            userKeys.map { String(it) } shouldContain "user:2:name"
            userKeys.map { String(it) } shouldContain "user:2:email"

            postKeys shouldHaveSize 2
            postKeys.map { String(it) } shouldContain "post:1:title"
            postKeys.map { String(it) } shouldContain "post:2:title"

            user1Keys shouldHaveSize 2
            user1Keys.map { String(it) } shouldContain "user:1:name"
            user1Keys.map { String(it) } shouldContain "user:1:email"
        }

        should("find key-value pairs by prefix") {
            // Arrange
            val testData = mapOf(
                "config:db:host".toByteArray() to "localhost".toByteArray(),
                "config:db:port".toByteArray() to "5432".toByteArray(),
                "config:app:name".toByteArray() to "MyApp".toByteArray(),
                "config:app:version".toByteArray() to "1.0.0".toByteArray(),
            )

            testData.forEach { (key, value) ->
                dbContext.put(ColumnFamily.DEFAULT, key, value)
            }

            // Act
            val configEntries = dbContext.findByPrefix(ColumnFamily.DEFAULT, "config:".toByteArray())
            val dbConfigEntries = dbContext.findByPrefix(ColumnFamily.DEFAULT, "config:db:".toByteArray())

            // Assert
            configEntries shouldHaveSize 4
            val configMap = configEntries.associate { String(it.first) to String(it.second) }
            configMap["config:db:host"] shouldBe "localhost"
            configMap["config:db:port"] shouldBe "5432"
            configMap["config:app:name"] shouldBe "MyApp"
            configMap["config:app:version"] shouldBe "1.0.0"

            dbConfigEntries shouldHaveSize 2
            val dbConfigMap = dbConfigEntries.associate { String(it.first) to String(it.second) }
            dbConfigMap["config:db:host"] shouldBe "localhost"
            dbConfigMap["config:db:port"] shouldBe "5432"
        }

        should("return empty results for non-matching prefix") {
            // Arrange
            dbContext.put(ColumnFamily.DEFAULT, "existing:key".toByteArray(), "value".toByteArray())

            // Act & Assert
            dbContext.findKeysByPrefix(ColumnFamily.DEFAULT, "missing:".toByteArray()).shouldBeEmpty()
            dbContext.findByPrefix(ColumnFamily.DEFAULT, "missing:".toByteArray()).shouldBeEmpty()
        }

        should("return empty results for empty prefix") {
            // Arrange
            dbContext.put(ColumnFamily.DEFAULT, "some:key".toByteArray(), "value".toByteArray())

            // Act & Assert
            dbContext.findKeysByPrefix(ColumnFamily.DEFAULT, ByteArray(0)).shouldBeEmpty()
            dbContext.findByPrefix(ColumnFamily.DEFAULT, ByteArray(0)).shouldBeEmpty()
        }
    }
}
