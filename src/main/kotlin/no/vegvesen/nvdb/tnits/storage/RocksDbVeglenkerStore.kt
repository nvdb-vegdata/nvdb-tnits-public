package no.vegvesen.nvdb.tnits.storage

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.serialization.kryo
import org.rocksdb.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

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
    }

    suspend fun initialize() =
        withContext(Dispatchers.IO) {
            options =
                Options().apply {
                    setCreateIfMissing(true)
                    setWriteBufferSize(64 * 1024 * 1024) // 64MB
                    setMaxWriteBufferNumber(3)
                    setMaxBackgroundCompactions(10)
                    setCompressionType(if (enableCompression) CompressionType.LZ4_COMPRESSION else CompressionType.NO_COMPRESSION)
                    setLevelCompactionDynamicLevelBytes(true)
                }

            db = RocksDB.open(options, dbPath)
        }

    override suspend fun get(veglenkesekvensId: Long): List<Veglenke>? =
        withContext(Dispatchers.IO) {
            val key = veglenkesekvensId.toByteArray()
            val value = db.get(key)

            value?.let { deserializeVeglenker(it) }
        }

    override suspend fun getAll(): Map<Long, List<Veglenke>> =
        withContext(Dispatchers.IO) {
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

            result
        }

    override suspend fun upsert(
        veglenkesekvensId: Long,
        veglenker: List<Veglenke>,
    ): Unit =
        withContext(Dispatchers.IO) {
            val key = veglenkesekvensId.toByteArray()
            val value = serializeVeglenker(veglenker)

            db.put(key, value)
        }

    override suspend fun delete(veglenkesekvensId: Long): Unit =
        withContext(Dispatchers.IO) {
            val key = veglenkesekvensId.toByteArray()
            db.delete(key)
        }

    override suspend fun batchUpdate(updates: Map<Long, List<Veglenke>?>): Unit =
        withContext(Dispatchers.IO) {
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

    override suspend fun size(): Long =
        withContext(Dispatchers.IO) {
            db.getProperty("rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L
        }

    override suspend fun clear(): Unit =
        withContext(Dispatchers.IO) {
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

    private fun serializeVeglenker(veglenker: List<Veglenke>): ByteArray {
        val baos = ByteArrayOutputStream()
        Output(baos).use { output ->
            kryo.writeObject(output, veglenker)
        }
        return baos.toByteArray()
    }

    private fun deserializeVeglenker(data: ByteArray): List<Veglenke> {
        val bais = ByteArrayInputStream(data)
        Input(bais).use { input ->
            @Suppress("UNCHECKED_CAST")
            return kryo.readObject(input, ArrayList::class.java) as List<Veglenke>
        }
    }

    private fun Long.toByteArray(): ByteArray {
        val bytes = ByteArray(8)
        bytes[0] = (this shr 56).toByte()
        bytes[1] = (this shr 48).toByte()
        bytes[2] = (this shr 40).toByte()
        bytes[3] = (this shr 32).toByte()
        bytes[4] = (this shr 24).toByte()
        bytes[5] = (this shr 16).toByte()
        bytes[6] = (this shr 8).toByte()
        bytes[7] = this.toByte()
        return bytes
    }

    private fun ByteArray.toLong(): Long {
        require(size >= 8) { "ByteArray must be at least 8 bytes long" }
        return (this[0].toLong() and 0xFF shl 56) or
            (this[1].toLong() and 0xFF shl 48) or
            (this[2].toLong() and 0xFF shl 40) or
            (this[3].toLong() and 0xFF shl 32) or
            (this[4].toLong() and 0xFF shl 24) or
            (this[5].toLong() and 0xFF shl 16) or
            (this[6].toLong() and 0xFF shl 8) or
            (this[7].toLong() and 0xFF)
    }
}
