package no.vegvesen.nvdb.tnits.storage

import org.rocksdb.*
import java.io.File

open class RocksDbConfiguration(
    protected val dbPath: String = "veglenker.db",
    private val enableCompression: Boolean = true,
) : AutoCloseable {
    private lateinit var db: RocksDB
    private lateinit var options: Options
    private lateinit var columnFamilies: Map<String, ColumnFamilyHandle>

    companion object {
        private var libraryLoaded = false

        const val DEFAULT_COLUMN_FAMILY = "default"
        const val NODER_COLUMN_FAMILY = "noder"
        const val VEGLENKER_COLUMN_FAMILY = "veglenker"

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

            // Define all column families we want to support
            val requiredColumnFamilies =
                listOf(
                    DEFAULT_COLUMN_FAMILY,
                    NODER_COLUMN_FAMILY,
                    VEGLENKER_COLUMN_FAMILY,
                )

            val columnFamilyDescriptors = mutableListOf<ColumnFamilyDescriptor>()

            // Always add default column family first
            columnFamilyDescriptors.add(ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions))

            // Add other column families if they exist or if it's a new database
            requiredColumnFamilies.drop(1).forEach { columnFamilyName ->
                if (existingColumnFamilies.contains(columnFamilyName) || !File(dbPath).exists()) {
                    columnFamilyDescriptors.add(ColumnFamilyDescriptor(columnFamilyName.toByteArray(), columnFamilyOptions))
                }
            }

            val columnFamilyHandles = mutableListOf<ColumnFamilyHandle>()
            val dbOptions = DBOptions(options)
            db = RocksDB.open(dbOptions, dbPath, columnFamilyDescriptors, columnFamilyHandles)

            // Build map of column family names to handles
            val columnFamilyMap = mutableMapOf<String, ColumnFamilyHandle>()

            // Map existing handles
            columnFamilyHandles.forEach { handle ->
                val name = String(handle.name)
                columnFamilyMap[name] = handle
            }

            // Create missing column families
            requiredColumnFamilies.forEach { columnFamilyName ->
                if (!columnFamilyMap.containsKey(columnFamilyName) && columnFamilyName != DEFAULT_COLUMN_FAMILY) {
                    val handle = db.createColumnFamily(ColumnFamilyDescriptor(columnFamilyName.toByteArray(), columnFamilyOptions))
                    columnFamilyMap[columnFamilyName] = handle
                }
            }

            columnFamilies = columnFamilyMap.toMap()
        } catch (e: RocksDBException) {
            throw RuntimeException("Failed to open RocksDB database at path: $dbPath", e)
        }

    fun getDatabase(): RocksDB = db

    fun getColumnFamily(name: String): ColumnFamilyHandle =
        columnFamilies[name] ?: throw IllegalArgumentException("Column family '$name' not found")

    fun getDefaultColumnFamily(): ColumnFamilyHandle = getColumnFamily(DEFAULT_COLUMN_FAMILY)

    fun getNoderColumnFamily(): ColumnFamilyHandle = getColumnFamily(NODER_COLUMN_FAMILY)

    fun getVeglenkerColumnFamily(): ColumnFamilyHandle = getColumnFamily(VEGLENKER_COLUMN_FAMILY)

    fun existsAndHasData(): Boolean =
        try {
            File(dbPath).exists() && getTotalSize() > 0
        } catch (e: Exception) {
            false
        }

    fun getTotalSize(): Long =
        columnFamilies.values.sumOf { columnFamily ->
            db.getProperty(columnFamily, "rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L
        }

    fun clear() {
        close()
        File(dbPath).deleteRecursively()
        initialize()
    }

    fun clearVeglenkerColumnFamily() {
        try {
            val veglenkerColumnFamily = getVeglenkerColumnFamily()
            db.deleteRange(veglenkerColumnFamily, ByteArray(0), ByteArray(8) { 0xFF.toByte() })
        } catch (e: RocksDBException) {
            throw RuntimeException("Failed to clear veglenker column family", e)
        }
    }

    override fun close() {
        if (::columnFamilies.isInitialized) {
            columnFamilies.values.forEach { columnFamily ->
                columnFamily.close()
            }
        }
        if (::db.isInitialized) {
            db.close()
        }
        if (::options.isInitialized) {
            options.close()
        }
    }
}
