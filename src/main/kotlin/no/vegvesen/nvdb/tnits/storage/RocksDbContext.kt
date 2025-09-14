package no.vegvesen.nvdb.tnits.storage

import org.rocksdb.*
import java.io.File

open class RocksDbContext(protected val dbPath: String = "veglenker.db", enableCompression: Boolean = true) : AutoCloseable {
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

    fun writeBatch(block: WriteBatchContext.() -> Unit) {
        val operations = WriteBatchContext(block)
        WriteBatch().use {
            for ((columnFamily, ops) in operations) {
                val handle = getColumnFamily(columnFamily)
                for (operation in ops) {
                    when (operation) {
                        is BatchOperation.Put -> it.put(handle, operation.key, operation.value)
                        is BatchOperation.Delete -> it.delete(handle, operation.key)
                    }
                }
            }

            WriteOptions().use { writeOpts ->
                db.write(writeOpts, it)
            }
        }
    }

    private fun initialize() = try {
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

    private fun createDatabaseOptions(): Options = Options().apply {
        prepareForBulkLoad()
        setCreateIfMissing(true)
        setCreateMissingColumnFamilies(true)
        setCompressionType(compression)
    }

    private fun createColumnFamilyOptions(): ColumnFamilyOptions = ColumnFamilyOptions().apply {
        setCompressionType(compression)
    }

    private fun detectExistingColumnFamilies(options: Options): Set<String> = try {
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

    private fun createColumnFamilyDescriptors(existingColumnFamilyNames: Set<String>, columnFamilyOptions: ColumnFamilyOptions): List<ColumnFamilyDescriptor> {
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

    fun getColumnFamily(columnFamily: ColumnFamily): ColumnFamilyHandle = columnFamilies[columnFamily]
        ?: throw IllegalArgumentException("Column family '${columnFamily.familyName}' not found")

    fun existsAndHasData(): Boolean = try {
        File(dbPath).exists() && getTotalSize() > 0
    } catch (e: Exception) {
        false
    }

    fun getTotalSize(): Long = columnFamilies.values.sumOf { columnFamily ->
        db.getProperty(columnFamily, "rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L
    }

    @Synchronized
    fun clear() {
        close()
        File(dbPath).deleteRecursively()
        initialize()
    }

    // Wrapper methods to encapsulate RocksDB operations
    fun get(columnFamily: ColumnFamily, key: ByteArray): ByteArray? {
        val handle = getColumnFamily(columnFamily)
        return db.get(handle, key)
    }

    fun put(columnFamily: ColumnFamily, key: ByteArray, value: ByteArray) {
        val handle = getColumnFamily(columnFamily)
        db.put(handle, key, value)
    }

    fun delete(columnFamily: ColumnFamily, key: ByteArray) {
        val handle = getColumnFamily(columnFamily)
        db.delete(handle, key)
    }

    fun getBatch(columnFamily: ColumnFamily, keys: Collection<ByteArray>): List<ByteArray?> {
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

    fun newIterator(columnFamily: ColumnFamily, readOptions: ReadOptions): RocksIterator {
        val handle = getColumnFamily(columnFamily)
        return db.newIterator(handle, readOptions)
    }

    fun deleteRange(columnFamily: ColumnFamily, start: ByteArray, end: ByteArray) {
        val handle = getColumnFamily(columnFamily)
        db.deleteRange(handle, start, end)
    }

    fun getEstimatedKeys(columnFamily: ColumnFamily): Long {
        val handle = getColumnFamily(columnFamily)
        return db.getProperty(handle, "rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L
    }

    fun findKeysByPrefix(columnFamily: ColumnFamily, prefix: ByteArray): List<ByteArray> {
        if (prefix.isEmpty()) {
            return emptyList()
        }

        val keys = mutableListOf<ByteArray>()

        val readOptions = ReadOptions().apply {
            setAutoPrefixMode(true)
            val upperBound = calculateUpperBound(prefix)
            setIterateUpperBound(upperBound)
        }

        readOptions.use { options ->
            newIterator(columnFamily, options).use { iterator ->
                iterator.seek(prefix)
                while (iterator.isValid && iterator.key().startsWith(prefix)) {
                    keys.add(iterator.key().clone())
                    iterator.next()
                }
            }
        }

        return keys
    }

    fun findByPrefix(columnFamily: ColumnFamily, prefix: ByteArray): List<Pair<ByteArray, ByteArray>> {
        if (prefix.isEmpty()) {
            return emptyList()
        }

        val keyValuePairs = mutableListOf<Pair<ByteArray, ByteArray>>()

        val readOptions = ReadOptions().apply {
            setAutoPrefixMode(true)
            val upperBound = calculateUpperBound(prefix)
            setIterateUpperBound(upperBound)
        }

        readOptions.use { options ->
            newIterator(columnFamily, options).use { iterator ->
                iterator.seek(prefix)
                while (iterator.isValid && iterator.key().startsWith(prefix)) {
                    keyValuePairs.add(Pair(iterator.key().clone(), iterator.value().clone()))
                    iterator.next()
                }
            }
        }

        return keyValuePairs
    }

    fun findValuesByPrefix(columnFamily: ColumnFamily, prefix: ByteArray): List<ByteArray> {
        if (prefix.isEmpty()) {
            return emptyList()
        }

        val values = mutableListOf<ByteArray>()

        val readOptions = ReadOptions().apply {
            setAutoPrefixMode(true)
            val upperBound = calculateUpperBound(prefix)
            setIterateUpperBound(upperBound)
        }

        readOptions.use { options ->
            newIterator(columnFamily, options).use { iterator ->
                iterator.seek(prefix)
                while (iterator.isValid && iterator.key().startsWith(prefix)) {
                    values.add(iterator.value().clone())
                    iterator.next()
                }
            }
        }

        return values
    }

    fun streamKeysByPrefix(columnFamily: ColumnFamily, prefix: ByteArray, startKey: ByteArray = prefix): Sequence<ByteArray> =
        scanByPrefixSequence(columnFamily, prefix, wantValue = false, startKey) { k, _ -> k }

    fun streamValuesByPrefix(columnFamily: ColumnFamily, prefix: ByteArray, startKey: ByteArray = prefix): Sequence<ByteArray> =
        scanByPrefixSequence(columnFamily, prefix, wantValue = true, startKey) { _, v -> v }

    fun streamEntriesByPrefix(columnFamily: ColumnFamily, prefix: ByteArray, startKey: ByteArray = prefix): Sequence<Pair<ByteArray, ByteArray>> =
        scanByPrefixSequence(columnFamily, prefix, wantValue = true, startKey) { k, v -> k to v }

    /** Streaming prefix scan as a Sequence. Closes RocksDB resources even on early termination. */
    private fun <T> scanByPrefixSequence(
        cf: ColumnFamily,
        prefix: ByteArray,
        wantValue: Boolean,
        startKey: ByteArray = prefix,
        map: (key: ByteArray, value: ByteArray) -> T,
    ): Sequence<T> = sequence {
        if (prefix.isEmpty()) return@sequence

        val upperBound = nextPrefixOrNull(prefix)

        val readOptions = ReadOptions()
            .setAutoPrefixMode(true)
            .setPrefixSameAsStart(true)
            .setFillCache(false)
            .apply {
                if (upperBound != null) setIterateUpperBound(upperBound)
                setTotalOrderSeek(false)
            }

        readOptions.use { ro ->
            newIterator(cf, ro).use {
                it.seek(startKey)

                val inRange: () -> Boolean =
                    if (upperBound != null) {
                        { it.isValid }
                    } else {
                        { it.isValid && hasPrefix(it.key(), prefix) }
                    }

                while (inRange()) {
                    val k = it.key().clone()
                    val v = if (wantValue) it.value().clone() else empty
                    yield(map(k, v))
                    it.next()
                }
            }
        }
    }

    private val empty = ByteArray(0)

    /** Compute the smallest byte array strictly greater than all keys with this prefix. Returns null if none (all 0xFF). */
    private fun nextPrefixOrNull(prefix: ByteArray): Slice? {
        val out = prefix.copyOf()
        for (i in out.indices.reversed()) {
            val b = out[i].toInt() and 0xFF
            if (b != 0xFF) {
                out[i] = (b + 1).toByte()
                for (j in i + 1 until out.size) out[j] = 0
                return Slice(out)
            }
        }
        return null
    }

    private fun hasPrefix(key: ByteArray, prefix: ByteArray): Boolean {
        if (key.size < prefix.size) return false
        for (i in prefix.indices) if (key[i] != prefix[i]) return false
        return true
    }

    private fun calculateUpperBound(prefix: ByteArray): Slice {
        val upperBound = prefix.clone()
        for (i in upperBound.size - 1 downTo 0) {
            if (upperBound[i] != Byte.MAX_VALUE) {
                upperBound[i] = (upperBound[i] + 1).toByte()
                return Slice(upperBound)
            }
        }
        return Slice(ByteArray(prefix.size + 1) { if (it < prefix.size) prefix[it] else 0 })
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    fun writeBatch(columnFamily: ColumnFamily, operations: List<BatchOperation>) {
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

    fun write(columnFamily: ColumnFamily, operation: BatchOperation) {
        when (operation) {
            is BatchOperation.Put -> put(columnFamily, operation.key, operation.value)
            is BatchOperation.Delete -> delete(columnFamily, operation.key)
        }
    }
}
