package no.vegvesen.nvdb.tnits.storage

import org.rocksdb.*
import java.io.File

open class RocksDbConfiguration(
    protected val dbPath: String = "veglenker.db",
    private val enableCompression: Boolean = true,
) : AutoCloseable {
    private lateinit var db: RocksDB
    private lateinit var options: Options
    private lateinit var columnFamilies: Map<ColumnFamily, ColumnFamilyHandle>

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
            options = createDatabaseOptions()
            val columnFamilyOptions = createColumnFamilyOptions()

            val existingColumnFamilyNames = detectExistingColumnFamilies(options)

            val columnFamilyDescriptors = createColumnFamilyDescriptors(existingColumnFamilyNames, columnFamilyOptions)

            val columnFamilyHandles = mutableListOf<ColumnFamilyHandle>()
            val dbOptionsForOpening = DBOptions(options)
            db = RocksDB.open(dbOptionsForOpening, dbPath, columnFamilyDescriptors, columnFamilyHandles)

            columnFamilies = mapColumnFamilyHandles(db, columnFamilyHandles, columnFamilyOptions)
        } catch (e: RocksDBException) {
            throw RuntimeException("Failed to open RocksDB database at path: $dbPath", e)
        }

    private fun createDatabaseOptions(): Options =
        Options().apply {
            prepareForBulkLoad()
            setCreateIfMissing(true)
            setCreateMissingColumnFamilies(true)
            setCompressionType(if (enableCompression) CompressionType.LZ4_COMPRESSION else CompressionType.NO_COMPRESSION)
        }

    private fun createColumnFamilyOptions(): ColumnFamilyOptions =
        ColumnFamilyOptions().apply {
            setCompressionType(if (enableCompression) CompressionType.LZ4_COMPRESSION else CompressionType.NO_COMPRESSION)
        }

    private fun detectExistingColumnFamilies(options: Options): Set<String> =
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

    private fun createColumnFamilyDescriptors(
        existingColumnFamilyNames: Set<String>,
        columnFamilyOptions: ColumnFamilyOptions,
    ): List<ColumnFamilyDescriptor> {
        val descriptors = mutableListOf<ColumnFamilyDescriptor>()

        // Always add default column family first
        descriptors.add(ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions))

        // Add all existing column families (required by RocksDB)
        existingColumnFamilyNames.forEach { familyName ->
            if (familyName != "default") { // Skip default as it's already added
                descriptors.add(ColumnFamilyDescriptor(familyName.toByteArray(), columnFamilyOptions))
            }
        }

        // Add any required column families that don't exist yet (will be auto-created)
        ColumnFamily.allFamilies().drop(1).forEach { columnFamily ->
            if (!existingColumnFamilyNames.contains(columnFamily.familyName)) {
                descriptors.add(
                    ColumnFamilyDescriptor(
                        columnFamily.familyName.toByteArray(),
                        columnFamilyOptions,
                    ),
                )
            }
        }

        return descriptors
    }

    private fun mapColumnFamilyHandles(
        db: RocksDB,
        columnFamilyHandles: List<ColumnFamilyHandle>,
        columnFamilyOptions: ColumnFamilyOptions,
    ): Map<ColumnFamily, ColumnFamilyHandle> {
        val requiredColumnFamilies = ColumnFamily.allFamilies()
        val columnFamilyMap = mutableMapOf<ColumnFamily, ColumnFamilyHandle>()

        // Map existing handles
        columnFamilyHandles.forEach { handle ->
            val name = String(handle.name)
            ColumnFamily.fromName(name)?.let { columnFamily ->
                columnFamilyMap[columnFamily] = handle
            }
        }

        // Create missing column families
        requiredColumnFamilies.forEach { columnFamily ->
            if (!columnFamilyMap.containsKey(columnFamily) && columnFamily != ColumnFamily.DEFAULT) {
                val handle =
                    db.createColumnFamily(
                        ColumnFamilyDescriptor(
                            columnFamily.familyName.toByteArray(),
                            columnFamilyOptions,
                        ),
                    )
                columnFamilyMap[columnFamily] = handle
            }
        }

        return columnFamilyMap.toMap()
    }

    fun getDatabase(): RocksDB = db

    fun getColumnFamily(columnFamily: ColumnFamily): ColumnFamilyHandle =
        columnFamilies[columnFamily]
            ?: throw IllegalArgumentException("Column family '${columnFamily.familyName}' not found")

    fun getColumnFamily(name: String): ColumnFamilyHandle =
        ColumnFamily.fromName(name)?.let { getColumnFamily(it) }
            ?: throw IllegalArgumentException("Column family '$name' not found")

    fun getDefaultColumnFamily(): ColumnFamilyHandle = getColumnFamily(ColumnFamily.DEFAULT)

    fun getNoderColumnFamily(): ColumnFamilyHandle = getColumnFamily(ColumnFamily.NODER)

    fun getVeglenkerColumnFamily(): ColumnFamilyHandle = getColumnFamily(ColumnFamily.VEGLENKER)

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

    @Synchronized
    fun clear() {
        close()
        File(dbPath).deleteRecursively()
        initialize()
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
