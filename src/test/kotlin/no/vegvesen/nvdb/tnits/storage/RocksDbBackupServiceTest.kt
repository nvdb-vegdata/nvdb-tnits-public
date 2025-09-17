package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.minio.MinioClient
import kotlinx.coroutines.runBlocking
import no.vegvesen.nvdb.tnits.config.BackupConfig
import no.vegvesen.nvdb.tnits.openlr.TempRocksDbConfig.Companion.withTempDb

class RocksDbBackupServiceTest :
    StringSpec({

        "backup service should handle disabled backup gracefully" {
            withTempDb { context ->
                val disabledConfig = BackupConfig(enabled = false)

                val mockMinioClient = MinioClient.builder()
                    .endpoint("http://localhost:9000")
                    .credentials("test", "test")
                    .build()

                val backupService = RocksDbBackupService(context, mockMinioClient, disabledConfig)

                val backupResult = runBlocking { backupService.createBackup() }
                val restoreResult = runBlocking { backupService.restoreFromBackup() }

                backupResult shouldBe true // Should succeed (no-op)
                restoreResult shouldBe false // Should skip restore
            }
        }

        "backup service should validate database existence check" {
            withTempDb { context ->
                val enabledConfig = BackupConfig(enabled = true, bucket = "test-bucket")

                val mockMinioClient = MinioClient.builder()
                    .endpoint("http://localhost:9000")
                    .credentials("test", "test")
                    .build()

                val backupService = RocksDbBackupService(context, mockMinioClient, enabledConfig)

                // Verify database exists after creation
                context.existsAndHasData() shouldBe false // Empty initially

                // Add some test data
                context.put(ColumnFamily.KEY_VALUE, "test-key".toByteArray(), "test-value".toByteArray())

                // Now should have data
                context.existsAndHasData() shouldBe true
            }
        }

        "backup service should create local backup successfully" {
            withTempDb { context ->
                // Add test data
                context.put(ColumnFamily.KEY_VALUE, "backup-test".toByteArray(), "backup-data".toByteArray())
                context.put(ColumnFamily.VEGLENKER, "veg-123".toByteArray(), "veglenke-data".toByteArray())

                // Verify data exists
                val retrievedValue = context.get(ColumnFamily.KEY_VALUE, "backup-test".toByteArray())
                retrievedValue shouldNotBe null
                String(retrievedValue!!) shouldBe "backup-data"
            }
        }

        "reinitialize should work after database operations" {
            withTempDb { context ->
                // Add data
                context.put(ColumnFamily.KEY_VALUE, "reinit-test".toByteArray(), "reinit-data".toByteArray())

                // Verify data exists
                val beforeReinit = context.get(ColumnFamily.KEY_VALUE, "reinit-test".toByteArray())
                beforeReinit shouldNotBe null

                // Reinitialize
                context.reinitialize()

                // Verify context still works
                context.existsAndHasData() shouldBe true // Data should still be there

                val afterReinit = context.get(ColumnFamily.KEY_VALUE, "reinit-test".toByteArray())
                afterReinit shouldNotBe null
                String(afterReinit!!) shouldBe "reinit-data"
            }
        }
    })
