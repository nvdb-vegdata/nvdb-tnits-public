package no.vegvesen.nvdb.tnits.storage

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.serialization.kryo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class FileVeglenkerStore(
    private val baseDir: String = "veglenker-store",
) : VeglenkerStore {
    private val cache = ConcurrentHashMap<Long, List<Veglenke>>()
    private val storageDir = File(baseDir)

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    override suspend fun get(veglenkesekvensId: Long): List<Veglenke>? =
        withContext(Dispatchers.IO) {
            // Check cache first
            cache[veglenkesekvensId] ?: run {
                // Load from disk
                val file = getFileForId(veglenkesekvensId)
                if (file.exists()) {
                    val veglenker = deserializeVeglenker(file)
                    cache[veglenkesekvensId] = veglenker
                    veglenker
                } else {
                    null
                }
            }
        }

    override suspend fun getAll(): Map<Long, List<Veglenke>> =
        withContext(Dispatchers.IO) {
            // Load all files in directory
            val result = mutableMapOf<Long, List<Veglenke>>()

            storageDir.listFiles { _, name -> name.endsWith(".kryo") }?.forEach { file ->
                val id = file.nameWithoutExtension.toLongOrNull()
                if (id != null) {
                    try {
                        val veglenker = deserializeVeglenker(file)
                        result[id] = veglenker
                        cache[id] = veglenker
                    } catch (e: Exception) {
                        println("Warning: Failed to load veglenker file ${file.name}: ${e.message}")
                    }
                }
            }

            result
        }

    override suspend fun upsert(
        veglenkesekvensId: Long,
        veglenker: List<Veglenke>,
    ): Unit =
        withContext(Dispatchers.IO) {
            val file = getFileForId(veglenkesekvensId)
            serializeVeglenker(veglenker, file)
            cache[veglenkesekvensId] = veglenker
        }

    override suspend fun delete(veglenkesekvensId: Long): Unit =
        withContext(Dispatchers.IO) {
            val file = getFileForId(veglenkesekvensId)
            if (file.exists()) {
                file.delete()
            }
            cache.remove(veglenkesekvensId)
        }

    override suspend fun batchUpdate(updates: Map<Long, List<Veglenke>?>): Unit =
        withContext(Dispatchers.IO) {
            for ((id, veglenker) in updates) {
                if (veglenker == null) {
                    delete(id)
                } else {
                    upsert(id, veglenker)
                }
            }
        }

    override suspend fun size(): Long =
        withContext(Dispatchers.IO) {
            storageDir.listFiles { _, name -> name.endsWith(".kryo") }?.size?.toLong() ?: 0L
        }

    override suspend fun clear(): Unit =
        withContext(Dispatchers.IO) {
            storageDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".kryo")) {
                    file.delete()
                }
            }
            cache.clear()
        }

    override fun close() {
        cache.clear()
    }

    private fun getFileForId(veglenkesekvensId: Long): File = File(storageDir, "$veglenkesekvensId.kryo")

    private fun serializeVeglenker(
        veglenker: List<Veglenke>,
        file: File,
    ) {
        FileOutputStream(file).use { fos ->
            Output(fos).use { output ->
                kryo.writeObject(output, veglenker)
            }
        }
    }

    private fun deserializeVeglenker(file: File): List<Veglenke> {
        FileInputStream(file).use { fis ->
            Input(fis).use { input ->
                @Suppress("UNCHECKED_CAST")
                return kryo.readObject(input, ArrayList::class.java) as List<Veglenke>
            }
        }
    }
}
