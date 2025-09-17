package no.vegvesen.nvdb.tnits.storage

import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.StatObjectArgs
import no.vegvesen.nvdb.tnits.config.BackupConfig
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import org.rocksdb.*
import java.io.*
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.exists

/**
 * Service for backing up and restoring RocksDB database to/from S3.
 * Provides manual backup functionality and startup restore capability.
 */
class RocksDbBackupService(private val rocksDbContext: RocksDbContext, private val minioClient: MinioClient, private val backupConfig: BackupConfig) :
    WithLogger {

    companion object {
        private const val BACKUP_OBJECT_NAME = "rocksdb-backup.tar.gz"
        private const val TEMP_BACKUP_DIR = "/tmp/nivdb-tnits-generator/backup"
        private const val BUFFER_SIZE = 8192
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
            log.info("Starting RocksDB backup...")

            val tempBackupPath = Paths.get(TEMP_BACKUP_DIR)

            // Clean up any existing temp backup directory
            if (tempBackupPath.exists()) {
                tempBackupPath.toFile().deleteRecursively()
            }

            // Create backup using RocksDB's BackupEngine
            createLocalBackup(tempBackupPath)

            // Compress and upload to S3
            val success = compressAndUploadBackup(tempBackupPath)

            // Clean up temp backup directory
            tempBackupPath.toFile().deleteRecursively()

            if (success) {
                log.info("RocksDB backup completed successfully")
            } else {
                log.error("RocksDB backup failed during upload")
            }

            success
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

            val tempRestorePath = Paths.get("temp-restore")

            // Clean up any existing temp restore directory
            if (tempRestorePath.exists()) {
                tempRestorePath.toFile().deleteRecursively()
            }

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
        val backupEngineOptions = BackupEngineOptions(backupPath.toString())
        BackupEngine.open(Env.getDefault(), backupEngineOptions).use { backupEngine ->
            backupEngine.createNewBackup(rocksDbContext.getDatabase())
        }
        log.debug("Local backup created at: $backupPath")
    }

    private fun compressAndUploadBackup(backupPath: Path): Boolean = try {
        ByteArrayOutputStream().use { byteArrayOut ->
            GZIPOutputStream(byteArrayOut).use { gzipOut ->
                compressDirectory(backupPath.toFile(), gzipOut)
            }

            val compressedData = byteArrayOut.toByteArray()
            log.debug("Compressed backup size: ${compressedData.size} bytes")

            ByteArrayInputStream(compressedData).use { inputStream ->
                val objectKey = "${backupConfig.path}/$BACKUP_OBJECT_NAME"
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(backupConfig.bucket)
                        .`object`(objectKey)
                        .stream(inputStream, compressedData.size.toLong(), -1)
                        .contentType("application/gzip")
                        .build(),
                )
            }

            log.info("Backup uploaded to S3: s3://${backupConfig.bucket}/${backupConfig.path}/$BACKUP_OBJECT_NAME")
            true
        }
    } catch (e: Exception) {
        log.error("Failed to compress and upload backup", e)
        false
    }

    private fun compressDirectory(directory: File, outputStream: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)

        fun addFileToStream(file: File, basePath: String) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    val childPath = if (basePath.isEmpty()) child.name else "$basePath/${child.name}"
                    addFileToStream(child, childPath)
                }
            } else {
                // Write file path length and path
                val pathBytes = basePath.toByteArray(Charsets.UTF_8)
                outputStream.write(pathBytes.size)
                outputStream.write(pathBytes)

                // Write file size
                val fileSize = file.length()
                val sizeBytes = ByteArray(8)
                for (i in 0..7) {
                    sizeBytes[i] = (fileSize shr (i * 8)).toByte()
                }
                outputStream.write(sizeBytes)

                // Write file content
                FileInputStream(file).use { fileInput ->
                    var bytesRead: Int
                    while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        addFileToStream(directory, "")
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
            GZIPInputStream(inputStream).use { gzipInput ->
                extractDirectory(gzipInput, restorePath.toFile())
            }
        }

        log.debug("Backup extracted to: $restorePath")
    }

    private fun extractDirectory(inputStream: InputStream, targetDirectory: File) {
        targetDirectory.mkdirs()
        val buffer = ByteArray(BUFFER_SIZE)

        while (true) {
            // Read path length
            val pathLengthByte = inputStream.read()
            if (pathLengthByte == -1) break

            // Read path
            val pathBytes = ByteArray(pathLengthByte)
            inputStream.readNBytes(pathBytes, 0, pathLengthByte)
            val relativePath = String(pathBytes, Charsets.UTF_8)

            // Read file size
            val sizeBytes = ByteArray(8)
            inputStream.readNBytes(sizeBytes, 0, 8)
            var fileSize = 0L
            for (i in 0..7) {
                fileSize = fileSize or ((sizeBytes[i].toLong() and 0xFF) shl (i * 8))
            }

            // Create target file
            val targetFile = File(targetDirectory, relativePath)
            targetFile.parentFile?.mkdirs()

            // Extract file content
            FileOutputStream(targetFile).use { fileOutput ->
                var totalRead = 0L
                while (totalRead < fileSize) {
                    val toRead = minOf(buffer.size.toLong(), fileSize - totalRead).toInt()
                    val bytesRead = inputStream.readNBytes(buffer, 0, toRead)
                    if (bytesRead == 0) break
                    fileOutput.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }
            }
        }
    }

    private fun restoreLocalDatabase(backupPath: Path) {
        // Close current RocksDB context to release locks
        rocksDbContext.close()

        // Delete existing database directory
        val dbPath = Paths.get(rocksDbContext.dbPath)
        if (dbPath.exists()) {
            dbPath.toFile().deleteRecursively()
        }

        // Restore from backup
        val restoreOptions = RestoreOptions(false)
        val options = Options().setCreateIfMissing(true)

        BackupEngine.open(Env.getDefault(), BackupEngineOptions(backupPath.toString())).use { backupEngine ->
            backupEngine.restoreDbFromLatestBackup(rocksDbContext.dbPath, rocksDbContext.dbPath, restoreOptions)
        }

        options.close()
        restoreOptions.close()

        // Reinitialize the RocksDB context
        rocksDbContext.reinitialize()

        log.debug("Database restored to: ${rocksDbContext.dbPath}")
    }
}
