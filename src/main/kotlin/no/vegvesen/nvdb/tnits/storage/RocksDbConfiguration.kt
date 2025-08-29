package no.vegvesen.nvdb.tnits.storage

import org.rocksdb.*
import java.io.File

class RocksDbConfiguration(
    private val dbPath: String = "veglenker.db",
    private val enableCompression: Boolean = true,
) : AutoCloseable {
    private lateinit var db: RocksDB
    private lateinit var options: Options
    private lateinit var defaultColumnFamily: ColumnFamilyHandle
    private lateinit var noderColumnFamily: ColumnFamilyHandle

    companion object {
        private var libraryLoaded = false

        @Synchronized
        private fun loadLibraryOnce() {
            if (!libraryLoaded) {
                try {
                    RocksDB.loadLibrary()
                    libraryLoaded = true
                } catch (e: Exception) {
                    throw RuntimeException(
                        "Failed to load RocksDB native library. " +
                            "This may indicate a dependency issue or platform compatibility problem.",
                        e,
                    )
                }
            }
        }
    }

    init {
        loadLibraryOnce()
        initialize()
    }

    private fun initialize() =
        try {
            options =
                Options().apply {
                    prepareForBulkLoad()
                    setCreateIfMissing(true)
                    setCreateMissingColumnFamilies(true)
                    setCompressionType(if (enableCompression) CompressionType.LZ4_COMPRESSION else CompressionType.NO_COMPRESSION)
                }

            val columnFamilyOptions =
                ColumnFamilyOptions().apply {
                    setCompressionType(if (enableCompression) CompressionType.LZ4_COMPRESSION else CompressionType.NO_COMPRESSION)
                }

            val existingColumnFamilies =
                try {
                    if (File(dbPath).exists()) {
                        RocksDB
                            .listColumnFamilies(options, dbPath)
                            .map { String(it) }
                            .toSet()
                    } else {
                        emptySet()
                    }
                } catch (e: RocksDBException) {
                    emptySet()
                }

            val columnFamilyDescriptors =
                mutableListOf(
                    ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions),
                )

            if (existingColumnFamilies.contains("noder") || !File(dbPath).exists()) {
                columnFamilyDescriptors.add(ColumnFamilyDescriptor("noder".toByteArray(), columnFamilyOptions))
            }

            val columnFamilyHandles = mutableListOf<ColumnFamilyHandle>()
            val dbOptions = DBOptions(options)
            db = RocksDB.open(dbOptions, dbPath, columnFamilyDescriptors, columnFamilyHandles)

            defaultColumnFamily = columnFamilyHandles[0]

            noderColumnFamily = columnFamilyHandles.find {
                String(it.name) == "noder"
            } ?: run {
                db.createColumnFamily(ColumnFamilyDescriptor("noder".toByteArray(), columnFamilyOptions))
            }
        } catch (e: RocksDBException) {
            throw RuntimeException("Failed to open RocksDB database at path: $dbPath", e)
        }

    fun getDatabase(): RocksDB = db

    fun getDefaultColumnFamily(): ColumnFamilyHandle = defaultColumnFamily

    fun getNoderColumnFamily(): ColumnFamilyHandle = noderColumnFamily

    fun existsAndHasData(): Boolean =
        try {
            File(dbPath).exists() && getTotalSize() > 0
        } catch (e: Exception) {
            false
        }

    fun getTotalSize(): Long =
        (db.getProperty(defaultColumnFamily, "rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L) +
            (db.getProperty(noderColumnFamily, "rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L)

    fun clear() {
        close()
        File(dbPath).deleteRecursively()
        initialize()
    }

    override fun close() {
        if (::defaultColumnFamily.isInitialized) {
            defaultColumnFamily.close()
        }
        if (::noderColumnFamily.isInitialized) {
            noderColumnFamily.close()
        }
        if (::db.isInitialized) {
            db.close()
        }
        if (::options.isInitialized) {
            options.close()
        }
    }
}
