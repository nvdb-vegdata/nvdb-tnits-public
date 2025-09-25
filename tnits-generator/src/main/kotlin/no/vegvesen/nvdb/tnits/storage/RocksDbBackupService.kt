package no.vegvesen.nvdb.tnits.storage

import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.StatObjectArgs
import no.vegvesen.nvdb.tnits.config.BackupConfig
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import no.vegvesen.nvdb.tnits.utilities.measure
import org.rocksdb.*
import org.slf4j.event.Level
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists

/**
 * Service for backing up and restoring RocksDB database to/from S3.
 * Provides manual backup functionality and startup restore capability.
 */
class RocksDbBackupService(private val rocksDbContext: RocksDbContext, private val minioClient: MinioClient, private val backupConfig: BackupConfig) :
    WithLogger {

    companion object {
        private const val BACKUP_OBJECT_NAME = "rocksdb-backup.zip"
        private const val BUFFER_SIZE = 2 * 1024 * 1024 // 2MB buffer for better I/O performance
    }

    /**
     * Creates a backup of the current RocksDB database and uploads it to S3.
     * Uses RocksDB's native BackupEngine for consistent snapshots.
     */
    fun createBackup(): Boolean {
        if (!backupConfig.enabled) {
            log.info("RocksDB backup is disabled")
            return true
        }

        return try {
            log.measure("Performing RocksDB backup", logStart = true) {
                log.debug("Backup config - bucket: ${backupConfig.bucket}, path: ${backupConfig.path}")

                // Create a proper temporary directory
                val tempBackupPath = Files.createTempDirectory("rocksdb-backup")
                log.debug("Using temp backup directory: {}", tempBackupPath)

                // Create backup using RocksDB's BackupEngine
                log.measure("Creating local backup using RocksDB BackupEngine", logStart = true, level = Level.DEBUG) {
                    createLocalBackup(tempBackupPath)
                }

                // Verify backup was created
                val backupFiles = tempBackupPath.toFile().listFiles()
                if (!tempBackupPath.exists() || backupFiles == null || backupFiles.isEmpty()) {
                    log.error("Local backup directory is empty or missing after backup creation")
                    return false
                }
                log.debug("Verified local backup exists with {} items", backupFiles.size)

                // Compress and upload to S3
                log.debug("Starting backup archive creation and S3 upload...")
                val success = compressAndUploadBackup(tempBackupPath)
                log.debug("Archive creation and upload result: {}", success)

                // Clean up temp backup directory
                log.debug("Cleaning up temp backup directory")
                tempBackupPath.toFile().deleteRecursively()

                if (success) {
                    log.info("RocksDB backup completed successfully")
                } else {
                    log.error("RocksDB backup failed during upload")
                }

                success
            }
        } catch (e: Exception) {
            log.error("Failed to create RocksDB backup", e)
            false
        }
    }

    /**
     * Attempts to restore RocksDB database from S3 backup.
     * Returns true if restore was successful, false if no backup exists or restore failed.
     */
    fun restoreFromBackup(): Boolean {
        if (!backupConfig.enabled) {
            log.info("RocksDB backup is disabled, skipping restore")
            return false
        }

        return try {
            log.info("Checking for RocksDB backup to restore...")

            // Check if backup exists in S3
            if (!backupExistsInS3()) {
                log.info("No RocksDB backup found in S3")
                return false
            }

            log.info("Found RocksDB backup in S3, starting restore...")

            // Create a proper temporary directory for restore
            val tempRestorePath = Files.createTempDirectory("rocksdb-restore")
            log.debug("Using temp restore directory: {}", tempRestorePath)

            // Download and extract backup
            downloadAndExtractBackup(tempRestorePath)

            // Restore database using RocksDB's RestoreEngine
            restoreLocalDatabase(tempRestorePath)

            // Clean up temp restore directory
            tempRestorePath.toFile().deleteRecursively()

            log.info("RocksDB restore completed successfully")
            true
        } catch (e: Exception) {
            log.error("Failed to restore RocksDB backup", e)
            false
        }
    }

    private fun createLocalBackup(backupPath: Path) {
        try {
            log.debug("Initializing BackupEngine with path: {}", backupPath)

            BackupEngineOptions(backupPath.toString()).use { backupEngineOptions ->

                BackupEngine.open(Env.getDefault(), backupEngineOptions).use { backupEngine ->
                    val database = rocksDbContext.getDatabase()

                    log.measure("Creating new backup", level = Level.DEBUG) {
                        backupEngine.createNewBackup(database)
                    }
                }
            }

            log.debug("Local backup created successfully at: {}", backupPath)
        } catch (e: Exception) {
            log.error("Failed to create local backup at $backupPath", e)
            throw e
        }
    }

    private fun compressAndUploadBackup(backupPath: Path): Boolean = try {
        log.measure("Creating and uploading backup archive: $backupPath", logStart = true) {
            val objectKey = "${backupConfig.path}/$BACKUP_OBJECT_NAME"
            log.debug("Starting backup upload to S3 - bucket: {}, key: {}", backupConfig.bucket, objectKey)

            S3OutputStream(minioClient, backupConfig.bucket, objectKey, "application/zip").use { s3Stream ->
                ZipOutputStream(s3Stream).use { zipOut ->
                    zipOut.setLevel(Deflater.NO_COMPRESSION)
                    addDirectoryToZip(backupPath.toFile(), zipOut)
                }
            }
            log.debug("Backup upload completed successfully")
            true
        }
    } catch (e: Exception) {
        log.error("Failed to create and upload backup", e)
        false
    }

    private fun addDirectoryToZip(directory: File, zipOut: ZipOutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)

        fun addFileToZip(file: File, basePath: String) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    val childPath = if (basePath.isEmpty()) child.name else "$basePath/${child.name}"
                    addFileToZip(child, childPath)
                }
            } else {
                val zipEntry = ZipEntry(basePath)
                zipEntry.time = file.lastModified()
                zipOut.putNextEntry(zipEntry)

                FileInputStream(file).use { fileInput ->
                    var bytesRead: Int
                    while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                        zipOut.write(buffer, 0, bytesRead)
                    }
                }

                zipOut.closeEntry()
            }
        }

        addFileToZip(directory, "")
    }

    private fun backupExistsInS3(): Boolean = try {
        val objectKey = "${backupConfig.path}/$BACKUP_OBJECT_NAME"
        minioClient.statObject(
            StatObjectArgs.builder()
                .bucket(backupConfig.bucket)
                .`object`(objectKey)
                .build(),
        )
        true
    } catch (e: Exception) {
        log.debug("Backup does not exist in S3: ${e.message}")
        false
    }

    private fun downloadAndExtractBackup(restorePath: Path) {
        val objectKey = "${backupConfig.path}/$BACKUP_OBJECT_NAME"

        minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(backupConfig.bucket)
                .`object`(objectKey)
                .build(),
        ).use { inputStream ->
            ZipInputStream(inputStream).use { zipInput ->
                extractZipDirectory(zipInput, restorePath.toFile())
            }
        }

        log.debug("Backup extracted to: {}", restorePath)
    }

    private fun extractZipDirectory(zipInput: ZipInputStream, targetDirectory: File) {
        targetDirectory.mkdirs()
        val buffer = ByteArray(BUFFER_SIZE)

        var entry = zipInput.nextEntry
        while (entry != null) {
            val targetFile = File(targetDirectory, entry.name)

            if (entry.isDirectory) {
                targetFile.mkdirs()
            } else {
                // Create parent directories if needed
                targetFile.parentFile?.mkdirs()

                // Extract file content
                FileOutputStream(targetFile).use { fileOutput ->
                    var bytesRead: Int
                    while (zipInput.read(buffer).also { bytesRead = it } != -1) {
                        fileOutput.write(buffer, 0, bytesRead)
                    }
                }

                // Preserve timestamp
                targetFile.setLastModified(entry.time)
            }

            zipInput.closeEntry()
            entry = zipInput.nextEntry
        }
    }

    private fun restoreLocalDatabase(backupPath: Path) {
        // Close current RocksDB context to release locks
        rocksDbContext.close()

        // Delete existing database directory
        val dbPath = Paths.get(rocksDbContext.dbPath)
        if (dbPath.exists()) {
            log.debug("Deleting existing database directory: {}", dbPath)
            dbPath.toFile().deleteRecursively()
        }

        // Restore from backup to the current context's database path
        val restoreOptions = RestoreOptions(false)
        val options = Options().setCreateIfMissing(true)

        try {
            BackupEngineOptions(backupPath.toString()).use { options ->
                BackupEngine.open(Env.getDefault(), options).use { backupEngine ->
                    log.debug("BackupEngine opened for restore, available backups:")
                    val backupInfos = backupEngine.backupInfo
                    backupInfos.forEach { info ->
                        log.debug("  Backup ID: ${info.backupId()}, Size: ${info.size()} bytes, Timestamp: ${info.timestamp()}")
                    }

                    log.debug("Starting restore from backup path: $backupPath to database path: ${rocksDbContext.dbPath}")
                    // Restore to current rocksDbContext.dbPath (both db_dir and wal_dir point to the same location)
                    backupEngine.restoreDbFromLatestBackup(rocksDbContext.dbPath, rocksDbContext.dbPath, restoreOptions)
                    log.debug("restoreDbFromLatestBackup completed")
                }
            }
        } catch (e: Exception) {
            log.error("Failed during RocksDB restore operation", e)
            throw e
        }

        options.close()
        restoreOptions.close()

        // Reinitialize the RocksDB context
        rocksDbContext.reinitialize()

        log.debug("Database restored to: ${rocksDbContext.dbPath}")
    }
}
