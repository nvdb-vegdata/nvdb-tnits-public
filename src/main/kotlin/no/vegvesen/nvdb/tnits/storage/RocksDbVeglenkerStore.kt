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
                    setCompressionType(if (enableCompression) CompressionType.LZ4_COMPRESSION else CompressionType.NO_COMPRESSION)
                }

            db = RocksDB.open(options, dbPath)
        } catch (e: RocksDBException) {
            throw RuntimeException("Failed to open RocksDB database at path: $dbPath", e)
        }

    override fun get(veglenkesekvensId: Long): List<Veglenke>? {
        val key = veglenkesekvensId.toByteArray()
        val value = db.get(key)

        return value?.let { deserializeVeglenker(it) }
    }

    override fun batchGet(veglenkesekvensIds: Collection<Long>): Map<Long, List<Veglenke>> {
        if (veglenkesekvensIds.isEmpty()) {
            return emptyMap()
        }

        val keys = veglenkesekvensIds.map { it.toByteArray() }
        val values = db.multiGetAsList(keys)

        val result = mutableMapOf<Long, List<Veglenke>>()
        veglenkesekvensIds.zip(values).forEach { (id, value) ->
            if (value != null) {
                result[id] = deserializeVeglenker(value)
            }
        }

        return result
    }

    override fun getAll(): Map<Long, List<Veglenke>> {
        val result = mutableMapOf<Long, List<Veglenke>>()

        db.newIterator().use { iterator ->
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

    override fun upsert(
        veglenkesekvensId: Long,
        veglenker: List<Veglenke>,
    ) {
        val key = veglenkesekvensId.toByteArray()
        val value = serializeVeglenker(veglenker)

        db.put(key, value)
    }

    override fun delete(veglenkesekvensId: Long) {
        val key = veglenkesekvensId.toByteArray()
        db.delete(key)
    }

    override fun batchUpdate(updates: Map<Long, List<Veglenke>?>) {
        val writeBatch = WriteBatch()

        try {
            for ((id, veglenker) in updates) {
                val key = id.toByteArray()
                if (veglenker == null) {
                    writeBatch.delete(key)
                } else {
                    val value = serializeVeglenker(veglenker)
                    writeBatch.put(key, value)
                }
            }

            WriteOptions().use { writeOpts ->
                db.write(writeOpts, writeBatch)
            }
        } finally {
            writeBatch.close()
        }
    }

    override fun size(): Long = db.getProperty("rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L

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
}
