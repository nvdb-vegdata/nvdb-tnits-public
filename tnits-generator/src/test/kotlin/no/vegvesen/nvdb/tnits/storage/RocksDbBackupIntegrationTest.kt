package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.config.BackupConfig
import no.vegvesen.nvdb.tnits.openlr.TempRocksDbConfig.Companion.withTempDb
import org.testcontainers.containers.MinIOContainer

class RocksDbBackupIntegrationTest :
    StringSpec({

        val minioContainer: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z")
            .withUserName("testuser")
            .withPassword("testpassword")
        lateinit var minioClient: MinioClient
        val testBucket = "nvdb-rocksdb-backups"

        beforeSpec {
            minioContainer.start()
            minioClient = MinioClient.builder()
                .endpoint(minioContainer.s3URL)
                .credentials(minioContainer.userName, minioContainer.password)
                .build()

            // Create test bucket
            minioClient.makeBucket(
                io.minio.MakeBucketArgs.builder()
                    .bucket(testBucket)
                    .build(),
            )
        }

        afterSpec {
            minioContainer.stop()
        }

        "should restore database from S3 backup" {
            withTempDb { dbContext ->
                val backupConfig = BackupConfig(
                    enabled = true,
                    bucket = testBucket,
                    path = "test-restore-path",
                )

                val backupService = RocksDbBackupService(dbContext, minioClient, backupConfig)

                // Add original test data
                dbContext.put(ColumnFamily.KEY_VALUE, "original-key".toByteArray(), "original-value".toByteArray())
                dbContext.put(ColumnFamily.VEGLENKER, "original-veglenke".toByteArray(), "original-veglenke-data".toByteArray())

                // Create backup
                val backupResult = backupService.createBackup()
                backupResult shouldBe true

                // Clear the database to simulate data loss
                dbContext.clear()

                // Verify database is now empty
                val emptyCheck = dbContext.get(ColumnFamily.KEY_VALUE, "original-key".toByteArray())
                emptyCheck shouldBe null

                // Set preserve on close to prevent deletion during restore
                dbContext.setPreserveOnClose(true)

                // Restore from backup
                val restoreResult = backupService.restoreFromBackup()
                restoreResult shouldBe true

                // Reset preserve flag
                dbContext.setPreserveOnClose(false)

                // Verify restored data
                val restoredKey = dbContext.get(ColumnFamily.KEY_VALUE, "original-key".toByteArray())
                restoredKey shouldNotBe null
                String(restoredKey!!) shouldBe "original-value"

                val restoredVeglenke = dbContext.get(ColumnFamily.VEGLENKER, "original-veglenke".toByteArray())
                restoredVeglenke shouldNotBe null
                String(restoredVeglenke!!) shouldBe "original-veglenke-data"
            }
        }
    })
