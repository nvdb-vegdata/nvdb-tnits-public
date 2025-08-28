package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.model.Veglenke
import org.rocksdb.*
import java.io.File
import java.nio.ByteBuffer

@OptIn(ExperimentalSerializationApi::class)
class RocksDbVeglenkerStore(
    private val dbPath: String = "veglenker.db",
    private val enableCompression: Boolean = true,
) : VeglenkerStore {
    private lateinit var db: RocksDB
    private lateinit var options: Options
    private lateinit var defaultColumnFamily: ColumnFamilyHandle // Used for veglenker data
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

    fun initialize() =
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

            // Try to list existing column families first
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

            // Assign column family handles
            defaultColumnFamily = columnFamilyHandles[0] // Default column family for veglenker data

            noderColumnFamily = columnFamilyHandles.find {
                String(it.name) == "noder"
            } ?: run {
                // Create noder column family if it doesn't exist
                db.createColumnFamily(ColumnFamilyDescriptor("noder".toByteArray(), columnFamilyOptions))
            }
        } catch (e: RocksDBException) {
            throw RuntimeException("Failed to open RocksDB database at path: $dbPath", e)
        }

    override fun getVeglenker(veglenkesekvensId: Long): List<Veglenke>? {
        val key = veglenkesekvensId.toByteArray()
        val value = db.get(defaultColumnFamily, key)

        return value?.let { deserializeVeglenker(it) }
    }

    override fun batchGetVeglenker(veglenkesekvensIds: Collection<Long>): Map<Long, List<Veglenke>> {
        if (veglenkesekvensIds.isEmpty()) {
            return emptyMap()
        }

        val columnFamilyHandleList = veglenkesekvensIds.map { defaultColumnFamily }
        val keys = veglenkesekvensIds.map { it.toByteArray() }
        val values = db.multiGetAsList(columnFamilyHandleList, keys)

        val result = mutableMapOf<Long, List<Veglenke>>()
        veglenkesekvensIds.zip(values).forEach { (id, value) ->
            if (value != null) {
                result[id] = deserializeVeglenker(value)
            }
        }

        return result
    }

    override fun getAllVeglenker(): Map<Long, List<Veglenke>> {
        val result = mutableMapOf<Long, List<Veglenke>>()

        db.newIterator(defaultColumnFamily).use { iterator ->
            iterator.seekToFirst()
            while (iterator.isValid) {
                val key =
                    ByteArray(8).let {
                        iterator.key().copyInto(it)
                        it.toLong()
                    }
                val veglenker = deserializeVeglenker(iterator.value())
                result[key] = veglenker
                iterator.next()
            }
        }

        return result
    }

    override fun getNodePortCount(nodeId: Long): Int? {
        val key = nodeId.toByteArray()
        val value = db.get(noderColumnFamily, key)

        return value?.let { it.toInt() }
    }

    override fun batchGetNodePortCounts(nodeIds: Collection<Long>): Map<Long, Int> {
        if (nodeIds.isEmpty()) {
            return emptyMap()
        }

        val columnFamilyHandleList = nodeIds.map { noderColumnFamily }
        val keys = nodeIds.map { it.toByteArray() }
        val values = db.multiGetAsList(columnFamilyHandleList, keys)

        val result = mutableMapOf<Long, Int>()
        nodeIds.zip(values).forEach { (id, value) ->
            if (value != null) {
                result[id] = value.toInt()
            }
        }

        return result
    }

    override fun upsertNodePortCount(
        nodeId: Long,
        portCount: Int,
    ) {
        val key = nodeId.toByteArray()
        val value = portCount.toByteArray()

        db.put(noderColumnFamily, key, value)
    }

    override fun deleteNodePortCount(nodeId: Long) {
        val key = nodeId.toByteArray()
        db.delete(noderColumnFamily, key)
    }

    override fun batchUpdateNodePortCounts(updates: Map<Long, Int?>) {
        val writeBatch = WriteBatch()

        try {
            for ((id, portCount) in updates) {
                val key = id.toByteArray()
                if (portCount == null) {
                    writeBatch.delete(noderColumnFamily, key)
                } else {
                    val value = portCount.toByteArray()
                    writeBatch.put(noderColumnFamily, key, value)
                }
            }

            WriteOptions().use { writeOpts ->
                db.write(writeOpts, writeBatch)
            }
        } finally {
            writeBatch.close()
        }
    }

    override fun upsertVeglenker(
        veglenkesekvensId: Long,
        veglenker: List<Veglenke>,
    ) {
        val key = veglenkesekvensId.toByteArray()
        val value = serializeVeglenker(veglenker)

        db.put(defaultColumnFamily, key, value)
    }

    override fun deleteVeglenker(veglenkesekvensId: Long) {
        val key = veglenkesekvensId.toByteArray()
        db.delete(defaultColumnFamily, key)
    }

    override fun batchUpdateVeglenker(updates: Map<Long, List<Veglenke>?>) {
        val writeBatch = WriteBatch()

        try {
            for ((id, veglenker) in updates) {
                val key = id.toByteArray()
                if (veglenker == null) {
                    writeBatch.delete(defaultColumnFamily, key)
                } else {
                    val value = serializeVeglenker(veglenker)
                    writeBatch.put(defaultColumnFamily, key, value)
                }
            }

            WriteOptions().use { writeOpts ->
                db.write(writeOpts, writeBatch)
            }
        } finally {
            writeBatch.close()
        }
    }

    override fun size(): Long =
        (db.getProperty(defaultColumnFamily, "rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L) +
            (db.getProperty(noderColumnFamily, "rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L)

    fun existsAndHasData(): Boolean =
        try {
            File(dbPath).exists() && size() > 0
        } catch (e: Exception) {
            false
        }

    override fun clear() {
        // Close existing database
        close()

        // Delete database directory
        File(dbPath).deleteRecursively()

        // Reinitialize
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

    private fun serializeVeglenker(veglenker: List<Veglenke>): ByteArray =
        ProtoBuf.encodeToByteArray(ListSerializer(Veglenke.serializer()), veglenker)

    private fun deserializeVeglenker(data: ByteArray): List<Veglenke> =
        ProtoBuf.decodeFromByteArray(ListSerializer(Veglenke.serializer()), data)

    private fun Long.toByteArray(): ByteArray = ByteBuffer.allocate(8).putLong(this).array()

    private fun ByteArray.toLong(): Long {
        require(size >= 8) { "ByteArray must be at least 8 bytes long" }
        return ByteBuffer.wrap(this).getLong()
    }

    private fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(4).putInt(this).array()

    private fun ByteArray.toInt(): Int {
        require(size >= 4) { "ByteArray must be at least 4 bytes long" }
        return ByteBuffer.wrap(this).getInt()
    }
}
