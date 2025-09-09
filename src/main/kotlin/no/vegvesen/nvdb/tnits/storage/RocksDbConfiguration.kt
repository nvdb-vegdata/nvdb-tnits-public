package no.vegvesen.nvdb.tnits.storage

import org.rocksdb.*
import java.io.File

open class RocksDbConfiguration(
    protected val dbPath: String = "veglenker.db",
    enableCompression: Boolean = true,
) : AutoCloseable {
    private lateinit var db: RocksDB
    private lateinit var options: Options
    private lateinit var dbOptions: DBOptions
    private lateinit var columnFamilyOptions: ColumnFamilyOptions
    private lateinit var columnFamilies: Map<ColumnFamily, ColumnFamilyHandle>

    private val compression = if (enableCompression) CompressionType.LZ4_COMPRESSION else CompressionType.NO_COMPRESSION

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
            columnFamilyOptions = createColumnFamilyOptions()

            val existingColumnFamilyNames = detectExistingColumnFamilies(options)

            val columnFamilyDescriptors = createColumnFamilyDescriptors(existingColumnFamilyNames, columnFamilyOptions)

            val columnFamilyHandles = mutableListOf<ColumnFamilyHandle>()
            dbOptions = DBOptions(options)
            db = RocksDB.open(dbOptions, dbPath, columnFamilyDescriptors, columnFamilyHandles)

            columnFamilies = mapColumnFamilyHandles(db, columnFamilyHandles, columnFamilyOptions)
        } catch (e: RocksDBException) {
            throw RuntimeException("Failed to open RocksDB database at path: $dbPath", e)
        }

    private fun createDatabaseOptions(): Options =
        Options().apply {
            prepareForBulkLoad()
            setCreateIfMissing(true)
            setCreateMissingColumnFamilies(true)
            setCompressionType(compression)
        }

    private fun createColumnFamilyOptions(): ColumnFamilyOptions =
        ColumnFamilyOptions().apply {
            setCompressionType(compression)
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

    // Wrapper methods to encapsulate RocksDB operations
    fun get(
        columnFamily: ColumnFamily,
        key: ByteArray,
    ): ByteArray? {
        val handle = getColumnFamily(columnFamily)
        return db.get(handle, key)
    }

    fun put(
        columnFamily: ColumnFamily,
        key: ByteArray,
        value: ByteArray,
    ) {
        val handle = getColumnFamily(columnFamily)
        db.put(handle, key, value)
    }

    fun delete(
        columnFamily: ColumnFamily,
        key: ByteArray,
    ) {
        val handle = getColumnFamily(columnFamily)
        db.delete(handle, key)
    }

    fun batchGet(
        columnFamily: ColumnFamily,
        keys: Collection<ByteArray>,
    ): List<ByteArray?> {
        if (keys.isEmpty()) {
            return emptyList()
        }
        val handle = getColumnFamily(columnFamily)
        val columnFamilyHandleList = keys.map { handle }
        return db.multiGetAsList(columnFamilyHandleList, keys.toList())
    }

    fun newIterator(columnFamily: ColumnFamily): RocksIterator {
        val handle = getColumnFamily(columnFamily)
        return db.newIterator(handle)
    }

    fun deleteRange(
        columnFamily: ColumnFamily,
        start: ByteArray,
        end: ByteArray,
    ) {
        val handle = getColumnFamily(columnFamily)
        db.deleteRange(handle, start, end)
    }

    fun getEstimatedKeys(columnFamily: ColumnFamily): Long {
        val handle = getColumnFamily(columnFamily)
        return db.getProperty(handle, "rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L
    }

    fun batchWrite(
        columnFamily: ColumnFamily,
        operations: List<BatchOperation>,
    ) {
        WriteBatch().use { writeBatch ->

            val handle = getColumnFamily(columnFamily)

            for (operation in operations) {
                when (operation) {
                    is BatchOperation.Put -> writeBatch.put(handle, operation.key, operation.value)
                    is BatchOperation.Delete -> writeBatch.delete(handle, operation.key)
                }
            }

            WriteOptions().use { writeOpts ->
                db.write(writeOpts, writeBatch)
            }

        }
    }

    sealed class BatchOperation {
        class Put(
            val key: ByteArray,
            val value: ByteArray,
        ) : BatchOperation()

        class Delete(
            val key: ByteArray,
        ) : BatchOperation()
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
        if (::dbOptions.isInitialized) {
            dbOptions.close()
        }
        if (::columnFamilyOptions.isInitialized) {
            columnFamilyOptions.close()
        }
        if (::options.isInitialized) {
            options.close()
        }
    }
}
