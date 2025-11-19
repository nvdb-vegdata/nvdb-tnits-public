package no.vegvesen.nvdb.tnits.generator.infrastructure

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.common.MinioTestHelper
import no.vegvesen.nvdb.tnits.common.infrastructure.MinioGateway
import no.vegvesen.nvdb.tnits.common.infrastructure.S3KeyValueStore
import no.vegvesen.nvdb.tnits.common.model.S3Config
import no.vegvesen.nvdb.tnits.generator.config.BackupConfig
import no.vegvesen.nvdb.tnits.generator.core.services.storage.ColumnFamily
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.RocksDbS3BackupService
import no.vegvesen.nvdb.tnits.generator.openlr.TempRocksDbConfig.Companion.withTempDb
import org.testcontainers.containers.MinIOContainer

class RocksDbBackupIntegrationTest : ShouldSpec() {

    private val minioContainer: MinIOContainer = MinioTestHelper.createMinioContainer()
    private lateinit var minioClient: MinioClient
    private val testBucket = "nvdb-rocksdb-backups"

    init {
        beforeSpec {
            minioContainer.start()
            minioClient = MinioTestHelper.createMinioClient(minioContainer)
            MinioTestHelper.waitForMinioReady(minioClient)
            MinioTestHelper.ensureBucketExists(minioClient, testBucket)
        }

        afterSpec {
            minioContainer.stop()
        }

        should("restore database from S3 backup") {
            withTempDb { dbContext ->
                val backupConfig = BackupConfig(
                    enabled = true,
                    path = "test-restore-path",
                )
                val s3Config = S3Config(
                    endpoint = "",
                    accessKey = "",
                    secretKey = "",
                    bucket = testBucket,
                )

                val backupService = RocksDbS3BackupService(
                    dbContext,
                    minioClient,
                    backupConfig,
                    s3Config = s3Config,
                    adminFlags = S3KeyValueStore(MinioGateway(minioClient, s3Config)),
                )

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
    }
}
